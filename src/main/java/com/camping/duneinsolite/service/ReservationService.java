package com.camping.duneinsolite.service;


import com.camping.duneinsolite.dto.request.*;
import com.camping.duneinsolite.dto.response.CampingStatsResponse;
import com.camping.duneinsolite.dto.response.ReservationResponse;
import com.camping.duneinsolite.model.enums.CompanyType;
import com.camping.duneinsolite.model.enums.ReservationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReservationService {
    ReservationResponse createReservation(ReservationRequest request);
    ReservationResponse getReservationById(UUID reservationId);
    List<ReservationResponse> getAllReservations();
    List<ReservationResponse> getReservationsByUser(UUID userId);
    List<ReservationResponse> getReservationsByStatus(ReservationStatus status);
    ReservationResponse updateReservationStatus(UUID reservationId, ReservationStatus status, String rejectionReason, CompanyType companyType);
    ReservationResponse updateReservation(UUID reservationId, ReservationUpdateRequest request);
    void deleteReservation(UUID reservationId);
    List<ReservationResponse> getMyReservations(UUID userId);
    List<ReservationResponse> searchReservationsByName(String name);


    ReservationResponse addStaffToReservation(UUID reservationId, ReservationStaffRequest request);

    ReservationResponse updateGuide(UUID reservationId, UUID guideId, GuideUpdateRequest request);
    String deleteGuide(UUID reservationId, UUID guideId);
    ReservationResponse updateChauffeur(UUID reservationId, UUID chauffeurId, ChauffeurUpdateRequest request);
    String deleteChauffeur(UUID reservationId, UUID chauffeurId);


    List<ReservationResponse> getActiveReservations();
    List<ReservationResponse> getActiveReservationsByDate(LocalDate date);

    List<ReservationResponse> getReservationsByDate(LocalDate date);
    // camping
    List<ReservationResponse> getCampingActiveReservations();
    List<ReservationResponse> getCampingActiveReservationsByDate(LocalDate date);
    List<ReservationResponse> searchCampingReservationsByName(String name);
    List<ReservationResponse> getCampingReservationsByStatus(ReservationStatus status);

    CampingStatsResponse getCampingStats();

    ReservationResponse recalculateCurrency(UUID reservationId);

}
