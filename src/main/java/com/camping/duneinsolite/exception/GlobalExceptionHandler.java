package com.camping.duneinsolite.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Handle @Valid validation errors on @RequestBody ───────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed — please check the fields below",
                fieldErrors
        );
    }

    // ── Handle @Valid on path/query params ────────────────────────────
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex) {

        Map<String, String> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage
                ));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                fieldErrors
        );
    }

    // ── Handle business logic errors (RuntimeException) ───────────────
    // This catches all the throw new RuntimeException(...) in your services
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                null
        );
    }

    // ── Handle 404 not found ──────────────────────────────────────────
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(
            jakarta.persistence.EntityNotFoundException ex) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                null
        );
    }

    // ── Handle any other unexpected error ─────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                null
        );
    }

    // ── Helper to build consistent error response ─────────────────────
    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String message, Map<String, String> errors) {

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (errors != null && !errors.isEmpty()) {
            body.put("errors", errors);
        }

        return ResponseEntity.status(status).body(body);
    }

    // ── Handle reservation status business rules ──────────────────────
    @ExceptionHandler(ReservationStatusException.class)
    public ResponseEntity<Map<String, Object>> handleReservationStatusException(
            ReservationStatusException ex) {

        return buildResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,  // 422 — more semantic than 400
                ex.getMessage(),
                null
        );
    }

    // ── Handle access denied (wrong role action) ──────────────────────
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {

        return buildResponse(
                HttpStatus.FORBIDDEN,   // 403
                ex.getMessage(),
                null
        );
    }

    // ── Handle user not found ─────────────────────────────────────────────
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // ── Handle duplicate email ────────────────────────────────────────────
    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<Map<String, Object>> handleEmailConflict(EmailAlreadyInUseException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null); // 409
    }

    // ── Handle Keycloak sync failures ────────────────────────────────────
    @ExceptionHandler(KeycloakSyncException.class)
    public ResponseEntity<Map<String, Object>> handleKeycloakSync(KeycloakSyncException ex) {
        return buildResponse(
                HttpStatus.BAD_GATEWAY,   // 502 — external service failed
                "Identity provider sync failed: " + ex.getMessage(),
                null
        );
    }
    // ── Handle repartition validation errors ──────────────────────────
    @ExceptionHandler(RepartitionValidationException.class)
    public ResponseEntity<Map<String, Object>> handleRepartitionValidation(
            RepartitionValidationException ex) {

        return buildResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,  // 422 — same as reservation status errors
                ex.getMessage(),
                null
        );
    }
}