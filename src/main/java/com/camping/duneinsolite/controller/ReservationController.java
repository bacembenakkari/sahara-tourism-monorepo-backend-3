package com.camping.duneinsolite.controller;


import com.camping.duneinsolite.dto.request.*;
import com.camping.duneinsolite.dto.response.CampingStatsResponse;
import com.camping.duneinsolite.dto.response.ReservationResponse;
import com.camping.duneinsolite.model.enums.CompanyType;
import com.camping.duneinsolite.model.enums.ReservationStatus;
import com.camping.duneinsolite.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
// ajoute d'une extras de la 1 ere fois
    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'PARTENAIRE','ADMIN', 'CAMPING')")
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(request));
    }

    @GetMapping("/{reservationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReservationResponse> getReservationById(@PathVariable UUID reservationId) {
        return ResponseEntity.ok(reservationService.getReservationById(reservationId));
    }
    @PostMapping("/{reservationId}/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReservationResponse> addStaff(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationStaffRequest request) {
        return ResponseEntity.ok(reservationService.addStaffToReservation(reservationId, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING', 'PARTENAIRE')")
    public ResponseEntity<List<ReservationResponse>> getAllReservations() {
        return ResponseEntity.ok(reservationService.getAllReservations());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING', 'CLIENT', 'PARTENAIRE')")
    public ResponseEntity<List<ReservationResponse>> getReservationsByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(reservationService.getReservationsByUser(userId));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING')")
    public ResponseEntity<List<ReservationResponse>> getReservationsByStatus(@PathVariable ReservationStatus status) {
        return ResponseEntity.ok(reservationService.getReservationsByStatus(status));
    }
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING', 'PARTENAIRE')")
    public ResponseEntity<List<ReservationResponse>> getActiveReservations(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date != null) {
            return ResponseEntity.ok(reservationService.getActiveReservationsByDate(date));
        }
        return ResponseEntity.ok(reservationService.getActiveReservations());
    }

    @PatchMapping("/{reservationId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING', 'PARTENAIRE', 'CLIENT')")
    public ResponseEntity<ReservationResponse> updateStatus(
            @PathVariable UUID reservationId,
            @RequestParam ReservationStatus status,
            @RequestParam(required = false) String rejectionReason,
            @RequestParam(required = false) CompanyType companyType) {
        return ResponseEntity.ok(reservationService.updateReservationStatus(reservationId, status, rejectionReason, companyType));
    }

    @PutMapping("/{reservationId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'PARTENAIRE')")
    public ResponseEntity<ReservationResponse> updateReservation(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationUpdateRequest request) {
        return ResponseEntity.ok(reservationService.updateReservation(reservationId, request));
    }

    @DeleteMapping("/{reservationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteReservation(@PathVariable UUID reservationId) {
        reservationService.deleteReservation(reservationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-reservations")
    @PreAuthorize("hasAnyRole('CLIENT', 'PARTENAIRE')")
    public ResponseEntity<List<ReservationResponse>> getMyReservations() {
        String userId = SecurityContextHolder.getContext()
                .getAuthentication().getName(); // gets the userId from the JWT
        return ResponseEntity.ok(reservationService.getMyReservations(UUID.fromString(userId)));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING')")
    public ResponseEntity<List<ReservationResponse>> searchByName(
            @RequestParam String name) {
        return ResponseEntity.ok(reservationService.searchReservationsByName(name));
    }
    @GetMapping("/by-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING')")
    public ResponseEntity<List<ReservationResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reservationService.getReservationsByDate(date));
    }

    // ── Staff management — ADMIN only ─────────────────────────────────────────────

    @PatchMapping("/{reservationId}/staff/guides/{guideId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReservationResponse> updateGuide(
            @PathVariable UUID reservationId,
            @PathVariable UUID guideId,
            @RequestBody GuideUpdateRequest request) {
        return ResponseEntity.ok(
                reservationService.updateGuide(reservationId, guideId, request));
    }

    @DeleteMapping("/{reservationId}/staff/guides/{guideId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteGuide(
            @PathVariable UUID reservationId,
            @PathVariable UUID guideId) {
        return ResponseEntity.ok(
                reservationService.deleteGuide(reservationId, guideId));
    }

    @PatchMapping("/{reservationId}/staff/chauffeurs/{chauffeurId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReservationResponse> updateChauffeur(
            @PathVariable UUID reservationId,
            @PathVariable UUID chauffeurId,
            @RequestBody ChauffeurUpdateRequest request) {
        return ResponseEntity.ok(
                reservationService.updateChauffeur(reservationId, chauffeurId, request));
    }

    @DeleteMapping("/{reservationId}/staff/chauffeurs/{chauffeurId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteChauffeur(
            @PathVariable UUID reservationId,
            @PathVariable UUID chauffeurId) {
        return ResponseEntity.ok(
                reservationService.deleteChauffeur(reservationId, chauffeurId));
    }
    @GetMapping("/camping/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING')")
    public ResponseEntity<List<ReservationResponse>> getCampingActiveReservations() {
        return ResponseEntity.ok(reservationService.getCampingActiveReservations());
    }

    @GetMapping("/camping/by-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING')")
    public ResponseEntity<List<ReservationResponse>> getCampingActiveByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reservationService.getCampingActiveReservationsByDate(date));
    }

    @GetMapping("/camping/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING')")
    public ResponseEntity<List<ReservationResponse>> searchCampingByName(
            @RequestParam String name) {
        return ResponseEntity.ok(reservationService.searchCampingReservationsByName(name));
    }

    @GetMapping("/camping/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING')")
    public ResponseEntity<List<ReservationResponse>> getCampingByStatus(
            @RequestParam ReservationStatus status) {
        return ResponseEntity.ok(reservationService.getCampingReservationsByStatus(status));
    }

    @GetMapping("/camping/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPING')")
    public ResponseEntity<CampingStatsResponse> getCampingStats() {
        return ResponseEntity.ok(reservationService.getCampingStats());
    }

    @PostMapping("/{reservationId}/recalculate-currency")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReservationResponse> recalculateCurrency(@PathVariable UUID reservationId) {
        return ResponseEntity.ok(reservationService.recalculateCurrency(reservationId));
    }
}

