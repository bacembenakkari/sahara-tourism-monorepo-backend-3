package com.camping.duneinsolite.service;

import com.camping.duneinsolite.dto.request.RegisterRequest;
import com.camping.duneinsolite.dto.request.UserRequest;
import com.camping.duneinsolite.exception.EmailAlreadyInUseException;
import com.camping.duneinsolite.exception.KeycloakSyncException;
import com.camping.duneinsolite.exception.UserNotFoundException;
import com.camping.duneinsolite.model.User;
import com.camping.duneinsolite.model.enums.LoyaltyTier;
import com.camping.duneinsolite.model.enums.UserRole;
import com.camping.duneinsolite.repository.UserRepository;
import com.camping.duneinsolite.service.impl.EmailService;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakUserSyncService {

    private final Keycloak keycloak;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${keycloak.realm}")
    private String realm;

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC SELF-REGISTRATION (CLIENT or PARTENAIRE registers themselves)
    // Called from POST /api/auth/register
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public User registerUser(RegisterRequest request) {
        // Password comes from the request — user chose it themselves
        String keycloakUserId = createKeycloakUser(
                request.getEmail(),
                request.getName(),
                request.getPassword()
        );

        assignRole(keycloakUserId, request.getRole().name());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("User already exists in local DB: " + request.getEmail());
        }

        User user = User.builder()
                .userId(UUID.fromString(keycloakUserId))
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(request.getRole())
                .loyaltyPoints(0)
                .loyaltyTier(LoyaltyTier.BRONZE)
                // PARTENAIRE fields
                .matriculeFiscal(request.getRole() == UserRole.PARTENAIRE ? request.getMatriculeFiscal() : null)
                .agencyAddress(request.getRole()   == UserRole.PARTENAIRE ? request.getAgencyAddress()   : null)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User {} registered and saved with id {}", request.getEmail(), savedUser.getUserId());
        return savedUser;
    }

    // ─────────────────────────────────────────────────────────────────────
    // ADMIN-CREATED USER (admin fills a form, password is generated)
    // Called from POST /api/users/add
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public User adminCreateUser(UserRequest request) {
        String generatedPassword = generateSecurePassword();
        log.info("Generated temporary password for new user {}", request.getEmail());

        String keycloakUserId = createKeycloakUser(
                request.getEmail(),
                request.getName(),
                generatedPassword
        );

        assignRole(keycloakUserId, request.getRole().name());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("User already exists in local DB: " + request.getEmail());
        }

        User user = User.builder()
                .userId(UUID.fromString(keycloakUserId))
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(request.getRole())
                .loyaltyPoints(0)
                .loyaltyTier(LoyaltyTier.BRONZE)
                .matriculeFiscal(request.getRole() == UserRole.PARTENAIRE ? request.getMatriculeFiscal() : null)
                .agencyAddress(request.getRole()   == UserRole.PARTENAIRE ? request.getAgencyAddress()   : null)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User {} created and saved with id {}", request.getEmail(), savedUser.getUserId());

        // ── Send welcome email with generated password ──
       // emailService.sendWelcomeEmail(request.getEmail(), request.getName(), generatedPassword);

        return savedUser;
    }

    // ─────────────────────────────────────────────────────────────────────
    // DELETE USER (Keycloak + local DB)
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        List<UserRepresentation> keycloakUsers = keycloak.realm(realm)
                .users()
                .searchByEmail(user.getEmail(), true);

        if (!keycloakUsers.isEmpty()) {
            keycloak.realm(realm)
                    .users()
                    .get(keycloakUsers.get(0).getId())
                    .remove();
            log.info("User {} deleted from Keycloak", user.getEmail());
        }

        userRepository.delete(user);
        log.info("User {} deleted from local DB", user.getEmail());
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE ROLE (Keycloak + local DB)
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public User updateUserRole(UUID userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        List<UserRepresentation> keycloakUsers = keycloak.realm(realm)
                .users()
                .searchByEmail(user.getEmail(), true);

        if (!keycloakUsers.isEmpty()) {
            String keycloakUserId = keycloakUsers.get(0).getId();
            RealmResource realmResource = keycloak.realm(realm);

            try {
                RoleRepresentation oldRole = realmResource.roles()
                        .get(user.getRole().name())
                        .toRepresentation();
                realmResource.users().get(keycloakUserId)
                        .roles().realmLevel().remove(List.of(oldRole));
            } catch (Exception e) {
                log.warn("Could not remove old role: {}", e.getMessage());
            }

            try {
                RoleRepresentation role = realmResource.roles()
                        .get(newRole.name())
                        .toRepresentation();
                realmResource.users().get(keycloakUserId)
                        .roles().realmLevel().add(List.of(role));
                log.info("User {} role updated to {} in Keycloak", user.getEmail(), newRole);
            } catch (Exception e) {
                log.warn("Could not assign new role: {}", e.getMessage());
            }
        }

        user.setRole(newRole);
        return userRepository.save(user);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates a user in Keycloak and returns the new Keycloak user ID.
     * Throws if user already exists (409) or creation fails.
     */
    private String createKeycloakUser(String email, String name, String password) {
        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false); // true = user must change password on first login

        UserRepresentation keycloakUser = new UserRepresentation();
        keycloakUser.setUsername(email);
        keycloakUser.setEmail(email);
        keycloakUser.setFirstName(name);
        keycloakUser.setEnabled(true);
        keycloakUser.setEmailVerified(true);
        keycloakUser.setCredentials(List.of(credential));

        Response response = usersResource.create(keycloakUser);
        int status = response.getStatus();

        if (status == 409) {
            throw new RuntimeException("User already exists in Keycloak: " + email);
        }
        if (status != 201) {
            String body = response.readEntity(String.class);
            throw new RuntimeException("Failed to create user in Keycloak. Status: " + status + " - " + body);
        }

        String locationHeader = response.getHeaderString("Location");
        String keycloakUserId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
        log.info("User {} created in Keycloak with id {}", email, keycloakUserId);
        return keycloakUserId;
    }

    /**
     * Assigns a realm role to a Keycloak user.
     * Auto-creates the role in Keycloak if it doesn't exist yet.
     */
    private void assignRole(String keycloakUserId, String roleName) {
        RealmResource realmResource = keycloak.realm(realm);
        try {
            List<RoleRepresentation> availableRoles = realmResource.roles().list();
            boolean roleExists = availableRoles.stream()
                    .anyMatch(r -> r.getName().equals(roleName));

            if (!roleExists) {
                RoleRepresentation newRole = new RoleRepresentation();
                newRole.setName(roleName);
                newRole.setDescription("Auto-created role for " + roleName);
                realmResource.roles().create(newRole);
                log.info("Role {} auto-created in Keycloak realm", roleName);
            }

            RoleRepresentation role = realmResource.roles()
                    .get(roleName)
                    .toRepresentation();

            realmResource.users()
                    .get(keycloakUserId)
                    .roles()
                    .realmLevel()
                    .add(List.of(role));

            log.info("Role {} assigned to user {} in Keycloak", roleName, keycloakUserId);
        } catch (Exception e) {
            log.warn("Could not assign role {} to user {}: {}", roleName, keycloakUserId, e.getMessage());
        }
    }

    /**
     * Generates a cryptographically secure random password.
     * Format: 3 uppercase + 3 lowercase + 3 digits + 3 special chars, shuffled.
     * Always satisfies common password policy requirements.
     */
    private String generateSecurePassword() {
        SecureRandom random = new SecureRandom();
        String upper   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower   = "abcdefghijklmnopqrstuvwxyz";
        String digits  = "0123456789";
        String special = "!@#$%&*";
        String all     = upper + lower + digits + special;

        StringBuilder sb = new StringBuilder();
        // Guarantee at least one of each required category
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(special.charAt(random.nextInt(special.length())));
        sb.append(special.charAt(random.nextInt(special.length())));
        // Fill remaining length
        for (int i = 8; i < 12; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        // Shuffle to avoid predictable pattern
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp;
        }
        return new String(chars);
    }

    @Transactional
    public User updateUser(UUID userId, UserRequest request) {

        // ── 1. Fetch existing user ──────────────────────────────────────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // ── 2. Email uniqueness check (only if email is being changed) ──────
        if (!user.getEmail().equalsIgnoreCase(request.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new EmailAlreadyInUseException(request.getEmail());
            }
        }

        // ── 3. Keycloak update ──────────────────────────────────────────────
        List<UserRepresentation> keycloakUsers = keycloak.realm(realm)
                .users()
                .searchByEmail(user.getEmail(), true); // search by OLD email

        if (!keycloakUsers.isEmpty()) {
            String keycloakUserId = keycloakUsers.get(0).getId();
            RealmResource realmResource = keycloak.realm(realm);

            // 3a. Update profile fields
            try {
                UserRepresentation keycloakUser = keycloakUsers.get(0);
                keycloakUser.setFirstName(request.getName());
                keycloakUser.setEmail(request.getEmail());
              //  keycloakUser.setUsername(request.getEmail()); // keep username = email
                keycloakUser.setEmailVerified(true);          // prevent 400 on email update
                keycloakUser.setEnabled(true);                // ensure user stays active
                keycloakUser.setRequiredActions(List.of());   // clear VERIFY_EMAIL etc.
                realmResource.users().get(keycloakUserId).update(keycloakUser);
                log.info("Keycloak profile updated for user {}", userId);

            } catch (jakarta.ws.rs.WebApplicationException e) {
                // Extract real Keycloak error body for debugging
                String keycloakError;
                try {
                    e.getResponse().bufferEntity();
                    keycloakError = e.getResponse().readEntity(String.class);
                } catch (Exception ignored) {
                    keycloakError = e.getMessage();
                }

                log.error("Keycloak rejected profile update for user {} — HTTP {}: {}",
                        userId, e.getResponse().getStatus(), keycloakError);

                if (e.getResponse().getStatus() == 409) {
                    throw new EmailAlreadyInUseException(request.getEmail());
                }
                throw new KeycloakSyncException(
                        "Keycloak error (" + e.getResponse().getStatus() + "): " + keycloakError, e);
            }

            // 3b. Update password only if explicitly provided
            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                try {
                    CredentialRepresentation credential = new CredentialRepresentation();
                    credential.setType(CredentialRepresentation.PASSWORD);
                    credential.setValue(request.getPassword());
                    credential.setTemporary(false);
                    realmResource.users().get(keycloakUserId).resetPassword(credential);
                    log.info("Password updated for user {} in Keycloak", userId);
                } catch (Exception e) {
                    throw new KeycloakSyncException(
                            "Failed to update password in Keycloak for user: " + userId, e);
                }
            }

            // 3c. Update role only if it actually changed
            if (!user.getRole().equals(request.getRole())) {

                // Remove old role
                try {
                    RoleRepresentation oldRole = realmResource.roles()
                            .get(user.getRole().name())
                            .toRepresentation();
                    realmResource.users()
                            .get(keycloakUserId)
                            .roles()
                            .realmLevel()
                            .remove(List.of(oldRole));
                    log.info("Old role {} removed from user {} in Keycloak",
                            user.getRole(), userId);
                } catch (jakarta.ws.rs.NotFoundException e) {
                    log.warn("Old role {} not found in Keycloak realm — skipping removal",
                            user.getRole().name());
                } catch (Exception e) {
                    log.warn("Could not remove old role {} from user {}: {}",
                            user.getRole(), userId, e.getMessage());
                }

                // Assign new role
                try {
                    assignRole(keycloakUserId, request.getRole().name());
                    log.info("New role {} assigned to user {} in Keycloak",
                            request.getRole(), userId);
                } catch (Exception e) {
                    throw new KeycloakSyncException(
                            "Failed to assign role " + request.getRole()
                                    + " in Keycloak for user: " + userId, e);
                }
            }

        } else {
            // User exists in DB but not in Keycloak — log and continue
            log.warn("User {} (ID: {}) not found in Keycloak — skipping Keycloak update",
                    user.getEmail(), userId);
        }

        // ── 4. Local DB update ──────────────────────────────────────────────
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());

        if (request.getRole() == UserRole.PARTENAIRE) {
            user.setMatriculeFiscal(request.getMatriculeFiscal());
            user.setAgencyAddress(request.getAgencyAddress());
        } else {
            // Clear partner-only fields if role changed away from PARTENAIRE
            user.setMatriculeFiscal(null);
            user.setAgencyAddress(null);
        }

        userRepository.save(user);
        log.info("User {} (ID: {}) updated successfully in DB", user.getEmail(), userId);

        // ── 5. Return updated entity ────────────────────────────────────────
        return user;
    }
}