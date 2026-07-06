package com.camping.duneinsolite.service.impl;

import com.camping.duneinsolite.config.CurrencyConfig;
import com.camping.duneinsolite.config.RabbitMQConfig;
import com.camping.duneinsolite.dto.message.NotificationMessage;
import com.camping.duneinsolite.dto.request.*;
import com.camping.duneinsolite.dto.response.CampingStatsResponse;
import com.camping.duneinsolite.dto.response.ReservationResponse;
import com.camping.duneinsolite.exception.RepartitionValidationException;
import com.camping.duneinsolite.exception.ReservationStatusException;
import com.camping.duneinsolite.mapper.ReservationMapper;
import com.camping.duneinsolite.model.*;
import com.camping.duneinsolite.model.enums.*;
import com.camping.duneinsolite.dto.request.TourHebergementRequest;
import com.camping.duneinsolite.dto.request.TourHebergementRepartitionRequest;
import com.camping.duneinsolite.repository.*;
import com.camping.duneinsolite.service.NotificationPublisher;
import com.camping.duneinsolite.service.ReservationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
 import com.camping.duneinsolite.model.Invoice;
 import com.camping.duneinsolite.model.InvoiceItem;
 import com.camping.duneinsolite.model.Transaction;
import com.camping.duneinsolite.dto.response.PaymentSummary;
 import com.camping.duneinsolite.dto.response.TransactionResponse;
 import com.camping.duneinsolite.repository.InvoiceRepository;
 import com.camping.duneinsolite.repository.TransactionRepository;
 import com.camping.duneinsolite.service.PaymentService;
 import com.camping.duneinsolite.mapper.TransactionMapper;
 import java.util.Comparator;
@Service
@RequiredArgsConstructor
@Transactional
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository      reservationRepository;
    private final UserRepository             userRepository;
    private final TourTypeRepository         tourTypeRepository;
    private final ExtraRepository            extraRepository;
    private final TourRepository             tourRepository;
    private final SourceRepository           sourceRepository;
    private final ReservationMapper          reservationMapper;
    private final NotificationPublisher      notificationPublisher;
    private final PaymentService paymentService;
    private final TransactionMapper transactionMapper;
    private final TransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrencyConfig             currencyConfig;

    @PersistenceContext
    private EntityManager entityManager;

    // ─────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────
        @Override
        public ReservationResponse createReservation(ReservationRequest request) {

            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));

            Source source = sourceRepository.findById(request.getSourceId())
                    .orElseThrow(() -> new RuntimeException("Source not found: " + request.getSourceId()));

            boolean isPartner = user.getRole() == UserRole.PARTENAIRE;

            int globalAdults   = request.getNumberOfAdults()   != null ? request.getNumberOfAdults()   : 0;
            int globalChildren = request.getNumberOfChildren() != null ? request.getNumberOfChildren() : 0;

            ReservationType type = request.getReservationType();

            validateReservationByType(request, type);

            Reservation reservation = buildBaseReservation(request, user, source, type, globalAdults, globalChildren);

            applyReservationItems(request, reservation, type, globalAdults, globalChildren, isPartner);

            // ── NEW: apply currency conversion if non-TND and initial payment provided ──


            if (request.getInitialPayment() != null
                    && request.getInitialPayment().getCurrency() != null
                    && request.getInitialPayment().getCurrency() != Currency.TND) {
                applyReservationCurrencyConversion(reservation, request.getInitialPayment().getCurrency());
            }
            Reservation savedReservation = reservationRepository.save(reservation);

            publishCreationNotification(savedReservation);

            handleInitialPayment(request, savedReservation);

            return toEnrichedResponse(savedReservation);
        }

    // ── Validation ────────────────────────────────────────────────────────────────

        private void validateReservationByType(ReservationRequest request, ReservationType type) {
            switch (type) {
                case HEBERGEMENT -> validateHebergement(request);
                case TOURS       -> validateTours(request);
                case EXTRAS      -> validateExtras(request);
            }
        }

        private void validateHebergement(ReservationRequest request) {
            if (request.getCheckInDate() == null || request.getCheckOutDate() == null) {
                throw new RuntimeException("Check-in and check-out dates are required for HEBERGEMENT reservations");
            }
            if (request.getTourTypes() == null || request.getTourTypes().isEmpty()) {
                throw new RuntimeException("At least one tour type is required for HEBERGEMENT reservations");
            }
        }

        private void validateTours(ReservationRequest request) {
            if (request.getTours() == null || request.getTours().isEmpty()) {
                throw new RuntimeException("A tour selection is required for TOURS reservations");
            }
            if (request.getTours().size() > 1) {
                throw new RuntimeException("Only one tour can be selected per reservation");
            }
            if (request.getServiceDate() == null) {
                throw new RuntimeException("Departure date (serviceDate) is required for TOURS reservations");
            }
        }

        private void validateExtras(ReservationRequest request) {
            if (request.getExtras() == null || request.getExtras().isEmpty()) {
                throw new RuntimeException("At least one extra is required for EXTRAS reservations");
            }
            if (request.getServiceDate() == null) {
                throw new RuntimeException("Service date is required for EXTRAS reservations");
            }
        }

        private void validateRepartitions(List<RepartitionRequest> repartitions, int globalAdults, int globalChildren) {
            if (repartitions == null || repartitions.isEmpty()) return;

            int globalTotal = globalAdults + globalChildren;

            repartitions.forEach(r -> {
                if (r.getNumberOfTentes() < 1) {
                    throw new RepartitionValidationException(
                            "Le nombre de tentes doit être au moins 1."
                    );
                }
            });

            // ── Total repartition must not EXCEED global group size ──
            int totalPersonnes = repartitions.stream()
                    .mapToInt(r -> r.getNumberOfTentes() * r.getTenteType().getCapacity())
                    .sum();

            if (totalPersonnes > globalTotal) {
                throw new RepartitionValidationException(
                        "La répartition des tentes (" + totalPersonnes + " personnes) " +
                                "dépasse le nombre total de personnes du groupe (" + globalTotal + ")."
                );
            }
        }
        private void applyRepartitions(List<RepartitionRequest> repartitions, Reservation reservation) {
            if (repartitions == null || repartitions.isEmpty()) return;

            repartitions.forEach(r -> {
                ReservationRepartition repartition = ReservationRepartition.builder()
                        .tenteType(r.getTenteType())
                        .numberOfTentes(r.getNumberOfTentes())
                        .build();
                reservation.addRepartition(repartition);
            });
        }

    // ── Base reservation builder ──────────────────────────────────────────────────

        private Reservation buildBaseReservation(ReservationRequest request, User user,
                                                 Source source,
                                                 ReservationType type,
                                                 int globalAdults, int globalChildren) {
            return Reservation.builder()
                    .user(user)
                    .sourceRef(source)
                    .reservationType(type)
                    .checkInDate(request.getCheckInDate())
                    .checkOutDate(request.getCheckOutDate())
                    .serviceDate(request.getServiceDate())
                    .groupName(request.getGroupName())
                    .groupLeaderName(request.getGroupLeaderName())
                    .demandeSpecial(request.getDemandeSpecial())
                    .numberOfAdults(globalAdults)
                    .numberOfChildren(globalChildren)
                    // ← use Currency enum, default TND if null
                    .currency(Currency.TND)
                    .promoCode(request.getPromoCode())
                    .status(ReservationStatus.PENDING)
                    .build();
        }

    // ── Items dispatcher ──────────────────────────────────────────────────────────

        private void applyReservationItems(ReservationRequest request, Reservation reservation,
                                           ReservationType type,
                                           int globalAdults, int globalChildren,
                                           boolean isPartner) {
            switch (type) {
                case HEBERGEMENT -> {
                    applyHebergementItems(request, reservation, globalAdults, globalChildren, isPartner);
                    // new validation of Repartition
                    validateRepartitions(request.getRepartitions(), globalAdults, globalChildren);
                    applyRepartitions(request.getRepartitions(), reservation);
                }
                case TOURS       -> applyToursItems(request, reservation, globalAdults, globalChildren, isPartner);
                case EXTRAS      -> reservation.setTotalAmount(null);
            }

            applyParticipants(request, reservation);
            applyExtras(request, reservation);

            reservation.setTotalExtrasAmount(reservation.calculateTotalExtrasAmount());
        }

    // ── HEBERGEMENT ───────────────────────────────────────────────────────────────

        private void applyHebergementItems(ReservationRequest request, Reservation reservation,
                                           int globalAdults, int globalChildren,
                                           boolean isPartner) {
            long nights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
            if (nights <= 0) nights = 1;

            boolean singleTourType = request.getTourTypes().size() == 1;

            if (!singleTourType) {
                validateTourTypePeopleCounts(request, globalAdults, globalChildren);
            }

            for (TourTypeSelectionRequest selection : request.getTourTypes()) {
                ReservationTourType snapshot = buildTourTypeSnapshot(
                        selection, singleTourType, globalAdults, globalChildren, nights, isPartner);
                reservation.addTourType(snapshot);
            }

            reservation.setTotalAmount(reservation.calculateTotalTourTypesAmount());
        }

        private void validateTourTypePeopleCounts(ReservationRequest request,
                                                  int globalAdults, int globalChildren) {
            for (TourTypeSelectionRequest selection : request.getTourTypes()) {
                int selAdults   = selection.getNumberOfAdults()   != null ? selection.getNumberOfAdults()   : 0;
                int selChildren = selection.getNumberOfChildren() != null ? selection.getNumberOfChildren() : 0;

                if (selAdults > globalAdults) {
                    throw new RuntimeException(
                            "Tour type adults (" + selAdults + ") cannot exceed group adults (" + globalAdults + ")");
                }
                if (selChildren > globalChildren) {
                    throw new RuntimeException(
                            "Tour type children (" + selChildren + ") cannot exceed group children (" + globalChildren + ")");
                }
            }

            int totalSelAdults = request.getTourTypes().stream()
                    .mapToInt(t -> t.getNumberOfAdults() != null ? t.getNumberOfAdults() : 0).sum();
            int totalSelChildren = request.getTourTypes().stream()
                    .mapToInt(t -> t.getNumberOfChildren() != null ? t.getNumberOfChildren() : 0).sum();

            if (totalSelAdults < globalAdults) {
                throw new RuntimeException(
                        "Total adults across all tour types (" + totalSelAdults + ") " +
                                "cannot be less than group adults (" + globalAdults + "). " +
                                "Every person must be assigned to at least one tour type.");
            }
            if (totalSelChildren < globalChildren) {
                throw new RuntimeException(
                        "Total children across all tour types (" + totalSelChildren + ") " +
                                "cannot be less than group children (" + globalChildren + "). " +
                                "Every child must be assigned to at least one tour type.");
            }
        }

        private ReservationTourType buildTourTypeSnapshot(TourTypeSelectionRequest selection,
                                                          boolean singleTourType,
                                                          int globalAdults, int globalChildren,
                                                          long nights, boolean isPartner) {
            TourType tourType = tourTypeRepository.findById(selection.getTourTypeId())
                    .orElseThrow(() -> new RuntimeException("TourType not found: " + selection.getTourTypeId()));

            int adults   = singleTourType ? globalAdults   : (selection.getNumberOfAdults()   != null ? selection.getNumberOfAdults()   : 0);
            int children = singleTourType ? globalChildren : (selection.getNumberOfChildren() != null ? selection.getNumberOfChildren() : 0);

            double adultPrice = isPartner ? tourType.getPartnerAdultPrice() : tourType.getPassengerAdultPrice();
            double childPrice = isPartner ? tourType.getPartnerChildPrice() : tourType.getPassengerChildPrice();

            return ReservationTourType.builder()
                    .name(tourType.getName())
                    .description(tourType.getDescription())
                    .duration(tourType.getDuration())
                    .adultPrice(adultPrice)
                    .childPrice(childPrice)
                    .numberOfAdults(adults)
                    .numberOfChildren(children)
                    .numberOfNights((int) nights)
                    .build();
        }

    // ── TOURS ─────────────────────────────────────────────────────────────────────

        private void applyToursItems(ReservationRequest request, Reservation reservation,
                                     int globalAdults, int globalChildren,
                                     boolean isPartner) {
            ReservationTour reservationTour = buildTourSnapshot(
                    request, globalAdults, globalChildren, isPartner);

            TourSelectionRequest tourSelection = request.getTours().get(0);
            if (tourSelection.getHebergements() != null && !tourSelection.getHebergements().isEmpty()) {
                applyTourHebergements(tourSelection.getHebergements(), reservationTour);
            }

            reservation.addTour(reservationTour);
            reservation.setTotalAmount(reservation.calculateTotalToursAmount());
        }

        private ReservationTour buildTourSnapshot(ReservationRequest request,
                                                  int globalAdults, int globalChildren,
                                                  boolean isPartner) {
            TourSelectionRequest selection = request.getTours().get(0);

            Tour tour = tourRepository.findById(selection.getTourId())
                    .orElseThrow(() -> new RuntimeException("Tour not found: " + selection.getTourId()));

            double adultPrice = isPartner ? tour.getPartnerAdultPrice() : tour.getPassengerAdultPrice();
            double childPrice = isPartner ? tour.getPartnerChildPrice() : tour.getPassengerChildPrice();
            double totalPrice = (globalAdults * adultPrice) + (globalChildren * childPrice);

            return ReservationTour.builder()
                    .catalogTourId(tour.getTourId())
                    .name(tour.getName())
                    .description(tour.getDescription())
                    .duration(tour.getDuration())
                    .adultPrice(adultPrice)
                    .childPrice(childPrice)
                    .numberOfAdults(globalAdults)
                    .numberOfChildren(globalChildren)
                    .departureDate(request.getServiceDate())
                    .totalPrice(totalPrice)
                    .build();
        }

        private void applyTourHebergements(List<TourHebergementRequest> hebergementRequests,
                                           ReservationTour reservationTour) {
            for (TourHebergementRequest req : hebergementRequests) {
                TourType tourType = tourTypeRepository.findById(req.getTourTypeId())
                        .orElseThrow(() -> new RuntimeException("TourType not found: " + req.getTourTypeId()));

                ReservationTourHebergement hebergement = ReservationTourHebergement.builder()
                        .name(tourType.getName())
                        .description(tourType.getDescription())
                        .duration(tourType.getDuration())
                        .numberOfNights(req.getNumberOfNights() != null ? req.getNumberOfNights() : 1)
                        .numberOfAdults(req.getNumberOfAdults())
                        .numberOfChildren(req.getNumberOfChildren())
                        .activityDate(req.getActivityDate())
                        .build();

                if (req.getRepartitions() != null) {
                    for (TourHebergementRepartitionRequest repReq : req.getRepartitions()) {
                        ReservationTourHebergementRepartition rep = ReservationTourHebergementRepartition.builder()
                                .tenteType(repReq.getTenteType())
                                .numberOfTentes(repReq.getNumberOfTentes())
                                .build();
                        hebergement.addRepartition(rep);
                    }
                }

                reservationTour.addHebergement(hebergement);
            }
        }

    // ── Participants ──────────────────────────────────────────────────────────────

        private void applyParticipants(ReservationRequest request, Reservation reservation) {
            if (request.getParticipants() == null) return;

            request.getParticipants().forEach(p -> {
                Participant participant = Participant.builder()
                        .fullName(p.getFullName())
                        .age(p.getAge())
                        .isAdult(p.getIsAdult())
                        .build();
                reservation.addParticipant(participant);
            });
        }

    // ── Extras ────────────────────────────────────────────────────────────────────

        private void applyExtras(ReservationRequest request, Reservation reservation) {
            if (request.getExtras() == null) return;

            request.getExtras().forEach(e -> {
                Extra catalog = extraRepository.findById(e.getExtraId())
                        .orElseThrow(() -> new RuntimeException("Extra not found: " + e.getExtraId()));

                ReservationExtra extra = ReservationExtra.builder()
                        .name(catalog.getName())
                        .description(catalog.getDescription())
                        .duration(catalog.getDuration())
                        .quantity(e.getQuantity())
                        .unitPrice(catalog.getUnitPrice())
                        .totalPrice(catalog.getUnitPrice() * e.getQuantity())
                        .isActive(true)
                        .build();
                reservation.addExtra(extra);
            });
        }

    // ── Notification ──────────────────────────────────────────────────────────────

        private void publishCreationNotification(Reservation savedReservation) {
            notificationPublisher.publish(
                    RabbitMQConfig.RESERVATION_CREATED,
                    NotificationMessage.builder()
                            .targetRoles(List.of(UserRole.ADMIN))
                            .type(NotificationType.RESERVATION_CREATED)
                            .reservationId(savedReservation.getReservationId())
                            .title("Nouvelle réservation")
                            .message("Le groupe \"" + savedReservation.getGroupName()
                                    + "\" a soumis une demande de réservation.")
                            .build()
            );
        }

    // ── Initial payment ───────────────────────────────────────────────────────────

        private void handleInitialPayment(ReservationRequest request, Reservation savedReservation) {
            if (request.getInitialPayment() == null) return;

            Transaction initialTx = paymentService.buildTransaction(savedReservation, request.getInitialPayment());
            transactionRepository.save(initialTx);
            paymentService.publishPaymentReceivedInternal(savedReservation, request.getInitialPayment().getAmount());

            PaymentSummary summary = paymentService.computePaymentSummary(savedReservation);
            if (summary.getPaymentStatus() == PaymentStatus.PAID) {
                paymentService.publishPaymentCompletedInternal(savedReservation);
            }
        }

    // ─────────────────────────────────────────────────────────────
    // ALL OTHER METHODS — UNTOUCHED FROM ORIGINAL
    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(UUID reservationId) {
        return toEnrichedResponse(findById(reservationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::toEnrichedResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByUser(UUID userId) {
        return reservationRepository.findByUserUserId(userId).stream()
                .map(this::toEnrichedResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByStatus(ReservationStatus status) {
        return reservationRepository.findByStatus(status).stream()
                .map(this::toEnrichedResponse).toList();
    }

    @Override
    public ReservationResponse updateReservationStatus(UUID reservationId, ReservationStatus status, String rejectionReason, CompanyType companyType) {
        Reservation reservation = findById(reservationId);

        ReservationStatus current = reservation.getStatus();

        if (current == ReservationStatus.COMPLETED) {
            throw new ReservationStatusException("This reservation is already completed and cannot be modified.");
        }
        if (current == ReservationStatus.CANCELLED) {
            throw new ReservationStatusException("This reservation has already been cancelled and cannot be modified.");
        }

        if (current == ReservationStatus.CHECKED_IN && status != ReservationStatus.COMPLETED) {
            throw new ReservationStatusException("A checked-in reservation can only be marked as completed.");
        }
        if (current == ReservationStatus.REJECTED) {
            throw new ReservationStatusException("This reservation has been rejected and cannot be modified.");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdminOrCamping = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_CAMPING"));

        if (!isAdminOrCamping) {
            if (status != ReservationStatus.CANCELLED) {
                throw new AccessDeniedException("You are not authorized to set this status. Only cancellation is allowed.");
            }

            LocalDateTime cancellationDeadline = reservation.getCheckInDate()
                    .atStartOfDay()
                    .minusHours(48);

            if (LocalDateTime.now().isAfter(cancellationDeadline)) {
                throw new ReservationStatusException(
                        "Cancellation is no longer possible. Reservations must be cancelled " +
                                "at least 48 hours before the check-in date (" + reservation.getCheckInDate() + ")."
                );
            }
        }

        reservation.setStatus(status);
        if (status == ReservationStatus.REJECTED) {
            reservation.setRejectionReason(rejectionReason);
        }
        if (status == ReservationStatus.COMPLETED) {
            reservation.setCompletedAt(LocalDateTime.now());
        }

        Reservation savedReservation = reservationRepository.save(reservation);

        if (status == ReservationStatus.CONFIRMED) {

            // ── Build confirmation message — include staff if already assigned ──
            boolean hasGuides     = !savedReservation.getGuides().isEmpty();
            boolean hasChauffeurs = !savedReservation.getChauffeurs().isEmpty();

            String confirmMessage;
            if (hasGuides || hasChauffeurs) {
                String guideNames = savedReservation.getGuides().stream()
                        .map(g -> g.getFirstName() + " " + g.getLastName())
                        .collect(Collectors.joining(", "));
                String chauffeurNames = savedReservation.getChauffeurs().stream()
                        .map(c -> c.getFirstName() + " " + c.getLastName())
                        .collect(Collectors.joining(", "));

                StringBuilder sb = new StringBuilder("Votre réservation pour le groupe \"")
                        .append(savedReservation.getGroupName()).append("\" a été confirmée.");
                if (hasGuides)     sb.append(" Guide(s): ").append(guideNames).append(".");
                if (hasChauffeurs) sb.append(" Chauffeur(s): ").append(chauffeurNames).append(".");
                confirmMessage = sb.toString();
            } else {
                confirmMessage = "Votre réservation pour le groupe \""
                        + savedReservation.getGroupName() + "\" a été confirmée.";
            }


            // ── Notify client/partenaire ──────────────────────────────────
            notificationPublisher.publish(
                    RabbitMQConfig.RESERVATION_CONFIRMED,
                    NotificationMessage.builder()
                            .targetUserId(savedReservation.getUser().getUserId())
                            .type(NotificationType.RESERVATION_CONFIRMED)
                            .reservationId(savedReservation.getReservationId())
                            .title("Réservation confirmée")
                            .message(confirmMessage)
                            .build()
            );

            // ── Notify CAMPING role — unchanged ──────────────────────────
            notificationPublisher.publish(
                    RabbitMQConfig.RESERVATION_CONFIRMED,
                    NotificationMessage.builder()
                            .targetRoles(List.of(UserRole.CAMPING))
                            .type(NotificationType.RESERVATION_CONFIRMED)
                            .reservationId(savedReservation.getReservationId())
                            .title("Nouvelle réservation confirmée")
                            .message("Le groupe \"" + savedReservation.getGroupName()
                                    + "\" arrive le " + savedReservation.getCheckInDate())
                            .build()
            );

            // ── Auto-generate PROFORMA invoice — UNCHANGED ────────────────
            double proformaTotal =
                    (savedReservation.getTotalAmount()       != null ? savedReservation.getTotalAmount()       : 0.0)
                            + (savedReservation.getTotalExtrasAmount() != null ? savedReservation.getTotalExtrasAmount() : 0.0);

            PaymentSummary alreadyPaid = paymentService.computePaymentSummary(savedReservation);
            double paidSoFar = alreadyPaid.getTotalPaid();

            PaymentStatus proformaPaymentStatus;
            if (paidSoFar <= 0) {
                proformaPaymentStatus = PaymentStatus.UNPAID;
            } else if (paidSoFar < proformaTotal) {
                proformaPaymentStatus = PaymentStatus.PARTIALLY_PAID;
            } else {
                proformaPaymentStatus = PaymentStatus.PAID;
            }

            Invoice proforma = Invoice.builder()
                    .invoiceNumber(generateProformaNumber())
                    .invoiceType(InvoiceType.PROFORMA)
                    .invoiceDate(LocalDate.now())
                    .totalAmount(proformaTotal)
                    .paidAmount(paidSoFar)
                    .status(InvoiceStatus.DRAFT)
                    .paymentStatus(proformaPaymentStatus)
                    .currency(savedReservation.getCurrency())
                    .reservation(savedReservation)
                    .user(savedReservation.getUser())
                    .companyType(companyType)
                    .build();

            int line = 1;
            // ── HEBERGEMENT: one line per tour type ───────────────────────
            // QTÉ = people assigned to this type, PRIX UNITAIRE = total stay / people
            if (savedReservation.getReservationType() == ReservationType.HEBERGEMENT
                    && savedReservation.getTourTypes() != null
                    && !savedReservation.getTourTypes().isEmpty()) {
                for (ReservationTourType tt : savedReservation.getTourTypes()) {
                    int totalPeople = (tt.getNumberOfAdults() != null ? tt.getNumberOfAdults() : 0)
                            + (tt.getNumberOfChildren() != null ? tt.getNumberOfChildren() : 0);
                    int qty = totalPeople > 0 ? totalPeople : 1;
                    // getTotalPrice() = (adults*adultPrice + children*childPrice) * nights
                    double totalStayPrice = tt.getTotalPrice();
                    double unitP = Math.round((totalStayPrice / qty) * 1000.0) / 1000.0;
                    proforma.addItem(InvoiceItem.builder()
                            .description(tt.getName())
                            .itemType("HEBERGEMENT")
                            .quantity(qty)
                            .unitPrice(unitP)
                            .lineNumber(line++)
                            .build());
                }
            // ── TOURS: one line per tour ──────────────────────────────────
            // QTÉ = people assigned, PRIX UNITAIRE = totalTourPrice / people
            } else if (savedReservation.getReservationType() == ReservationType.TOURS
                    && savedReservation.getTours() != null
                    && !savedReservation.getTours().isEmpty()) {
                for (ReservationTour t : savedReservation.getTours()) {
                    int totalPeople = (t.getNumberOfAdults() != null ? t.getNumberOfAdults() : 0)
                            + (t.getNumberOfChildren() != null ? t.getNumberOfChildren() : 0);
                    int qty = totalPeople > 0 ? totalPeople : 1;
                    double unitP = t.getTotalPrice() != null
                            ? Math.round((t.getTotalPrice() / qty) * 1000.0) / 1000.0 : 0.0;
                    proforma.addItem(InvoiceItem.builder()
                            .description(t.getName())
                            .itemType("TOURS")
                            .quantity(qty)
                            .unitPrice(unitP)
                            .lineNumber(line++)
                            .build());
                }
            }
            // ── EXTRAS: one line per active extra (runs for all reservation types) ──
            // QTÉ = extra.quantity (e.g. 7 people for quad), PRIX UNITAIRE = extra.unitPrice
            if (savedReservation.getExtras() != null) {
                for (ReservationExtra extra : savedReservation.getExtras()) {
                    if (Boolean.TRUE.equals(extra.getIsActive())) {
                        proforma.addItem(InvoiceItem.builder()
                                .description(extra.getName())
                                .itemType("EXTRA")
                                .quantity(extra.getQuantity() != null ? extra.getQuantity() : 1)
                                .unitPrice(extra.getUnitPrice() != null ? extra.getUnitPrice() : 0.0)
                                .lineNumber(line++)
                                .build());
                    }
                }
            }
            invoiceRepository.save(proforma);
        }
        if (status == ReservationStatus.COMPLETED) {

            if (companyType != null) {
                // ── Generate facture ──────────────────────────────────────
                double rawTotal =
                        (savedReservation.getTotalAmount()       != null ? savedReservation.getTotalAmount()       : 0.0)
                                + (savedReservation.getTotalExtrasAmount() != null ? savedReservation.getTotalExtrasAmount() : 0.0);

                Currency currency      = savedReservation.getCurrency() != null ? savedReservation.getCurrency() : Currency.TND;
                double timbreFiscal    = getTimbreFiscal(currency);
                double totalHt         = Math.round((rawTotal / 1.07) * 1000.0) / 1000.0;
                double tvaAmount       = Math.round((totalHt * 0.07) * 1000.0) / 1000.0;
                double totalTtc        = Math.round((totalHt + tvaAmount + timbreFiscal) * 1000.0) / 1000.0;

                PaymentSummary paid    = paymentService.computePaymentSummary(savedReservation);
                double paidSoFar       = paid.getTotalPaid();

                PaymentStatus facturePaymentStatus;
                if (paidSoFar <= 0)              facturePaymentStatus = PaymentStatus.UNPAID;
                else if (paidSoFar < totalTtc)   facturePaymentStatus = PaymentStatus.PARTIALLY_PAID;
                else                             facturePaymentStatus = PaymentStatus.PAID;

                LocalDate completedDate = savedReservation.getCompletedAt().toLocalDate();

                Invoice facture = Invoice.builder()
                        .invoiceNumber(generateFactureNumber())
                        .invoiceType(InvoiceType.STANDARD)
                        .invoiceDate(completedDate)
                        .totalAmount(rawTotal)
                        .totalHt(totalHt)
                        .tvaRate(7.0)
                        .tvaAmount(tvaAmount)
                        .timbreFiscal(timbreFiscal)
                        .totalTtc(totalTtc)
                        .paidAmount(paidSoFar)
                        .status(InvoiceStatus.DRAFT)
                        .paymentStatus(facturePaymentStatus)
                        .currency(currency)
                        .reservation(savedReservation)
                        .user(savedReservation.getUser())
                        .companyType(companyType)
                        .build();

                int line = 1;
                if (savedReservation.getReservationType() == ReservationType.HEBERGEMENT
                        && savedReservation.getTourTypes() != null
                        && !savedReservation.getTourTypes().isEmpty()) {
                    for (ReservationTourType tt : savedReservation.getTourTypes()) {
                        int totalPeople = (tt.getNumberOfAdults()   != null ? tt.getNumberOfAdults()   : 0)
                                       + (tt.getNumberOfChildren() != null ? tt.getNumberOfChildren() : 0);
                        int qty    = totalPeople > 0 ? totalPeople : 1;
                        double unitP = Math.round((tt.getTotalPrice() / qty) * 1000.0) / 1000.0;
                        facture.addItem(InvoiceItem.builder()
                                .description(tt.getName())
                                .itemType("HEBERGEMENT")
                                .quantity(qty)
                                .unitPrice(unitP)
                                .lineNumber(line++)
                                .build());
                    }
                } else if (savedReservation.getReservationType() == ReservationType.TOURS
                        && savedReservation.getTours() != null
                        && !savedReservation.getTours().isEmpty()) {
                    for (ReservationTour t : savedReservation.getTours()) {
                        int totalPeople = (t.getNumberOfAdults()   != null ? t.getNumberOfAdults()   : 0)
                                       + (t.getNumberOfChildren() != null ? t.getNumberOfChildren() : 0);
                        int qty    = totalPeople > 0 ? totalPeople : 1;
                        double unitP = t.getTotalPrice() != null
                                ? Math.round((t.getTotalPrice() / qty) * 1000.0) / 1000.0 : 0.0;
                        facture.addItem(InvoiceItem.builder()
                                .description(t.getName())
                                .itemType("TOURS")
                                .quantity(qty)
                                .unitPrice(unitP)
                                .lineNumber(line++)
                                .build());
                    }
                }
                if (savedReservation.getExtras() != null) {
                    for (ReservationExtra extra : savedReservation.getExtras()) {
                        if (Boolean.TRUE.equals(extra.getIsActive())) {
                            facture.addItem(InvoiceItem.builder()
                                    .description(extra.getName())
                                    .itemType("EXTRA")
                                    .quantity(extra.getQuantity() != null ? extra.getQuantity() : 1)
                                    .unitPrice(extra.getUnitPrice() != null ? extra.getUnitPrice() : 0.0)
                                    .lineNumber(line++)
                                    .build());
                        }
                    }
                }

                invoiceRepository.save(facture);

                notificationPublisher.publish(
                        RabbitMQConfig.RESERVATION_CONFIRMED,
                        NotificationMessage.builder()
                                .targetUserId(savedReservation.getUser().getUserId())
                                .type(NotificationType.RESERVATION_CONFIRMED)
                                .reservationId(savedReservation.getReservationId())
                                .title("Réservation terminée")
                                .message("Votre réservation pour le groupe \""
                                        + savedReservation.getGroupName()
                                        + "\" est terminée. Votre facture est disponible.")
                                .build()
                );
            } else {
                // ── Completed without generating a facture ────────────────
                notificationPublisher.publish(
                        RabbitMQConfig.RESERVATION_CONFIRMED,
                        NotificationMessage.builder()
                                .targetUserId(savedReservation.getUser().getUserId())
                                .type(NotificationType.RESERVATION_CONFIRMED)
                                .reservationId(savedReservation.getReservationId())
                                .title("Réservation terminée")
                                .message("Votre réservation pour le groupe \""
                                        + savedReservation.getGroupName()
                                        + "\" est terminée.")
                                .build()
                );
            }
        }

        if (status == ReservationStatus.REJECTED) {
            notificationPublisher.publish(
                    RabbitMQConfig.RESERVATION_REJECTED,
                    NotificationMessage.builder()
                            .targetUserId(savedReservation.getUser().getUserId())
                            .type(NotificationType.RESERVATION_REJECTED)
                            .title("Réservation rejetée")
                            .reservationId(savedReservation.getReservationId())
                            .message("Votre réservation pour le groupe \""
                                    + savedReservation.getGroupName() + "\" a été rejetée."
                                    + (savedReservation.getRejectionReason() != null
                                    ? " Raison: " + savedReservation.getRejectionReason() : ""))
                            .build()
            );
        }

        return toEnrichedResponse(savedReservation);
    }

    @Override
    public ReservationResponse updateReservation(UUID reservationId, ReservationUpdateRequest request) {
        Reservation reservation = findById(reservationId);

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email = jwt.getClaim("email");

        User authenticatedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        if (!reservation.getUser().getUserId().equals(authenticatedUser.getUserId())) {
            throw new AccessDeniedException("You can only edit your own reservations.");
        }

        if (reservation.getStatus() == ReservationStatus.CHECKED_IN  ||
                reservation.getStatus() == ReservationStatus.COMPLETED   ||
                reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Cannot edit a reservation with status: " + reservation.getStatus());
        }

        if (request.getCheckInDate()     != null) reservation.setCheckInDate(request.getCheckInDate());
        if (request.getCheckOutDate()    != null) reservation.setCheckOutDate(request.getCheckOutDate());
        if (request.getServiceDate()     != null) reservation.setServiceDate(request.getServiceDate());
        if (request.getGroupName()       != null) reservation.setGroupName(request.getGroupName());
        if (request.getGroupLeaderName() != null) reservation.setGroupLeaderName(request.getGroupLeaderName());
        if (request.getDemandeSpecial()  != null) reservation.setDemandeSpecial(request.getDemandeSpecial());
        if (request.getPromoCode()       != null) reservation.setPromoCode(request.getPromoCode());
        if (request.getNumberOfAdults()   != null) reservation.setNumberOfAdults(request.getNumberOfAdults());
        if (request.getNumberOfChildren() != null) reservation.setNumberOfChildren(request.getNumberOfChildren());

        if (reservation.getStatus() == ReservationStatus.CONFIRMED ||
                reservation.getStatus() == ReservationStatus.REJECTED) {
            reservation.setStatus(ReservationStatus.PENDING);
            reservation.setRejectionReason(null);
        }

        long nights = ChronoUnit.DAYS.between(reservation.getCheckInDate(), reservation.getCheckOutDate());
        if (nights <= 0) nights = 1;

        boolean isPartner = authenticatedUser.getRole() == UserRole.PARTENAIRE;

        if (request.getTourTypes() != null && !request.getTourTypes().isEmpty()) {
            int globalAdults   = reservation.getNumberOfAdults();
            int globalChildren = reservation.getNumberOfChildren();
            boolean singleTourType = request.getTourTypes().size() == 1;

            if (!singleTourType) {
                for (TourTypeSelectionRequest selection : request.getTourTypes()) {
                    int selAdults   = selection.getNumberOfAdults()   != null ? selection.getNumberOfAdults()   : 0;
                    int selChildren = selection.getNumberOfChildren() != null ? selection.getNumberOfChildren() : 0;

                    if (selAdults > globalAdults) throw new RuntimeException(
                            "Tour type adults (" + selAdults + ") cannot exceed group adults (" + globalAdults + ")"
                    );
                    if (selChildren > globalChildren) throw new RuntimeException(
                            "Tour type children (" + selChildren + ") cannot exceed group children (" + globalChildren + ")"
                    );
                }

                int totalSelAdults = request.getTourTypes().stream()
                        .mapToInt(t -> t.getNumberOfAdults() != null ? t.getNumberOfAdults() : 0).sum();
                int totalSelChildren = request.getTourTypes().stream()
                        .mapToInt(t -> t.getNumberOfChildren() != null ? t.getNumberOfChildren() : 0).sum();

                if (totalSelAdults < globalAdults) throw new RuntimeException(
                        "Total adults across all tour types (" + totalSelAdults + ") cannot be less than group adults (" + globalAdults + ")."
                );
                if (totalSelChildren < globalChildren) throw new RuntimeException(
                        "Total children across all tour types (" + totalSelChildren + ") cannot be less than group children (" + globalChildren + ")."
                );
            }

            reservation.getTourTypes().clear();

            Currency resCurrency = reservation.getCurrency() != null ? reservation.getCurrency() : Currency.TND;
            double tourTypeRate = switch (resCurrency) {
                case EUR -> currencyConfig.getEurRate();
                case USD -> currencyConfig.getUsdRate();
                case TND -> 1.0;
            };

            for (TourTypeSelectionRequest selection : request.getTourTypes()) {
                TourType tourType = tourTypeRepository.findById(selection.getTourTypeId())
                        .orElseThrow(() -> new RuntimeException("TourType not found: " + selection.getTourTypeId()));

                int adults   = singleTourType ? globalAdults   : (selection.getNumberOfAdults()   != null ? selection.getNumberOfAdults()   : 0);
                int children = singleTourType ? globalChildren : (selection.getNumberOfChildren() != null ? selection.getNumberOfChildren() : 0);

                double adultPrice = isPartner ? tourType.getPartnerAdultPrice() : tourType.getPassengerAdultPrice();
                double childPrice = isPartner ? tourType.getPartnerChildPrice() : tourType.getPassengerChildPrice();

                ReservationTourType snapshot = ReservationTourType.builder()
                        .name(tourType.getName())
                        .description(tourType.getDescription())
                        .duration(tourType.getDuration())
                        .adultPrice(r2(adultPrice / tourTypeRate))
                        .childPrice(r2(childPrice / tourTypeRate))
                        .numberOfAdults(adults)
                        .numberOfChildren(children)
                        .numberOfNights((int) nights)
                        .build();

                reservation.addTourType(snapshot);
            }
            reservation.setTotalAmount(reservation.calculateTotalTourTypesAmount());
        }

        if (request.getParticipants() != null) {
            reservation.getParticipants().clear();
            request.getParticipants().forEach(p -> {
                Participant participant = Participant.builder()
                        .fullName(p.getFullName())
                        .age(p.getAge())
                        .isAdult(p.getIsAdult())
                        .build();
                reservation.addParticipant(participant);
            });
        }

        if (request.getExtras() != null) {
            Currency extraCurrency = reservation.getCurrency() != null ? reservation.getCurrency() : Currency.TND;
            double extraRate = switch (extraCurrency) {
                case EUR -> currencyConfig.getEurRate();
                case USD -> currencyConfig.getUsdRate();
                case TND -> 1.0;
            };

            reservation.getExtras().clear();
            request.getExtras().forEach(e -> {
                Extra catalog = extraRepository.findById(e.getExtraId())
                        .orElseThrow(() -> new RuntimeException("Extra not found: " + e.getExtraId()));

                double unitPrice  = r2(catalog.getUnitPrice() / extraRate);
                double totalPrice = r2(unitPrice * e.getQuantity());

                ReservationExtra extra = ReservationExtra.builder()
                        .name(catalog.getName())
                        .description(catalog.getDescription())
                        .duration(catalog.getDuration())
                        .quantity(e.getQuantity())
                        .unitPrice(unitPrice)
                        .totalPrice(totalPrice)
                        .isActive(true)
                        .build();
                reservation.addExtra(extra);
            });
            reservation.setTotalExtrasAmount(reservation.calculateTotalExtrasAmount());
        }
        if (request.getRepartitions() != null) {
            int globalAdults   = reservation.getNumberOfAdults();
            int globalChildren = reservation.getNumberOfChildren();
            validateRepartitions(request.getRepartitions(), globalAdults, globalChildren);
            reservation.getRepartitions().clear();
            applyRepartitions(request.getRepartitions(), reservation);
        }

        // ── TOURS: update hebergements if provided ────────────────────────────────
        if (request.getHebergements() != null
                && reservation.getReservationType() == ReservationType.TOURS
                && !reservation.getTours().isEmpty()) {
            ReservationTour reservationTour = reservation.getTours().get(0);
            reservationTour.getHebergements().clear();
            applyTourHebergements(request.getHebergements(), reservationTour);
        }

        Reservation savedReservation = reservationRepository.save(reservation);

        notificationPublisher.publish(
                RabbitMQConfig.RESERVATION_UPDATED,
                NotificationMessage.builder()
                        .targetRoles(List.of(UserRole.ADMIN))
                        .type(NotificationType.RESERVATION_UPDATED)
                        .reservationId(savedReservation.getReservationId())
                        .title("Réservation modifiée")
                        .message("Le groupe \"" + savedReservation.getGroupName()
                                + "\" a modifié sa réservation. En attente de reconfirmation.")
                        .build()
        );

        return toEnrichedResponse(savedReservation);
    }

    @Override
    public void deleteReservation(UUID reservationId) {
        reservationRepository.delete(findById(reservationId));
    }

    @Override
    public List<ReservationResponse> getMyReservations(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return reservationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toEnrichedResponse)
                .collect(Collectors.toList());
    }

    private Reservation findById(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));
    }
    private String generateProformaNumber() {
        // PRO-00001 format — separate sequence from INV-
        long count = invoiceRepository.count() + 1;
        return String.format("PRO-%05d", count);
    }
    private String generateFactureNumber() {
        int year = LocalDate.now().getYear();
        long count = invoiceRepository.countByInvoiceType(InvoiceType.STANDARD) + 1;
        return String.format("FAC-%d/%d", count, year);
    }
    private ReservationResponse toEnrichedResponse(Reservation reservation) {
        ReservationResponse response = reservationMapper.toResponse(reservation);
        PaymentSummary summary = paymentService.computePaymentSummary(reservation);
        response.setPaymentSummary(summary);
        // Always override with snapshot-derived amounts so stale DB values never reach the frontend
        response.setTotalAmount(summary.getOriginalMainAmount());
        response.setTotalExtrasAmount(summary.getOriginalExtrasAmount());
        List<TransactionResponse> txHistory = transactionRepository
                .findByReservationReservationId(reservation.getReservationId())
                .stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .map(transactionMapper::toResponse)
                .toList();
        response.setTransactions(txHistory);
        return response;
    }
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> searchReservationsByName(String name) {
        return reservationRepository.searchByUserName(name).stream()
                .map(this::toEnrichedResponse)
                .toList();
    }
    // ── Staff ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ReservationResponse addStaffToReservation(UUID reservationId, ReservationStaffRequest request) {

        Reservation reservation = findById(reservationId);
        validateIsTourReservation(reservation);
        validateStaffManageable(reservation);      // ← ADD

        if ((request.getGuides() == null || request.getGuides().isEmpty()) &&
                (request.getChauffeurs() == null || request.getChauffeurs().isEmpty())) {
            throw new RuntimeException("At least one guide or chauffeur must be provided");
        }

        if (request.getGuides() != null && !request.getGuides().isEmpty()) {
            request.getGuides().forEach(g -> {
                Guide guide = Guide.builder()
                        .firstName(g.getFirstName())
                        .lastName(g.getLastName())
                        .phoneNumber(g.getPhoneNumber())
                        .build();
                reservation.addGuide(guide);
            });
        }

        if (request.getChauffeurs() != null && !request.getChauffeurs().isEmpty()) {
            request.getChauffeurs().forEach(c -> {
                Chauffeur chauffeur = Chauffeur.builder()
                        .firstName(c.getFirstName())
                        .lastName(c.getLastName())
                        .phoneNumber(c.getPhoneNumber())
                        .build();
                reservation.addChauffeur(chauffeur);
            });
        }

        Reservation savedReservation = reservationRepository.save(reservation);

        boolean isActiveReservation = savedReservation.getStatus() == ReservationStatus.CONFIRMED
                || savedReservation.getStatus() == ReservationStatus.CHECKED_IN;

        if (isActiveReservation) {
            String guideNames = savedReservation.getGuides().stream()
                    .map(g -> g.getFirstName() + " " + g.getLastName())
                    .collect(Collectors.joining(", "));
            String chauffeurNames = savedReservation.getChauffeurs().stream()
                    .map(c -> c.getFirstName() + " " + c.getLastName())
                    .collect(Collectors.joining(", "));

            StringBuilder msg = new StringBuilder("Du personnel a été assigné à votre réservation ")
                    .append(getReservationLabel(savedReservation)).append(".");
            if (!guideNames.isEmpty())     msg.append(" Guide(s): ").append(guideNames).append(".");
            if (!chauffeurNames.isEmpty()) msg.append(" Chauffeur(s): ").append(chauffeurNames).append(".");

            notificationPublisher.publish(
                    RabbitMQConfig.STAFF_ASSIGNED,
                    NotificationMessage.builder()
                            .targetUserId(savedReservation.getUser().getUserId())
                            .type(NotificationType.STAFF_ASSIGNED)
                            .reservationId(savedReservation.getReservationId())
                            .title("Personnel assigné")
                            .message(msg.toString())
                            .build()
            );
        }

        return toEnrichedResponse(savedReservation);
    }

// ── Guide management ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ReservationResponse updateGuide(UUID reservationId, UUID guideId, GuideUpdateRequest request) {

        Reservation reservation = findById(reservationId);
        validateIsTourReservation(reservation);
        validateStaffManageable(reservation);      // ← ADD

        Guide guide = reservation.getGuides().stream()
                .filter(g -> g.getGuideId().equals(guideId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Guide not found: " + guideId + " in reservation: " + reservationId));

        if (request.getFirstName()   != null) guide.setFirstName(request.getFirstName());
        if (request.getLastName()    != null) guide.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) guide.setPhoneNumber(request.getPhoneNumber());

        Reservation savedReservation = reservationRepository.save(reservation);

        if (isActiveReservation(savedReservation)) {
            notificationPublisher.publish(
                    RabbitMQConfig.STAFF_UPDATED,
                    NotificationMessage.builder()
                            .targetUserId(savedReservation.getUser().getUserId())
                            .type(NotificationType.STAFF_UPDATED)
                            .reservationId(savedReservation.getReservationId())
                            .title("Personnel modifié")
                            .message("Le guide de votre réservation "
                                    + getReservationLabel(savedReservation) + " a été mis à jour.")
                            .build()
            );
        }

        return toEnrichedResponse(savedReservation);
    }

    @Override
    @Transactional
    public String deleteGuide(UUID reservationId, UUID guideId) {

        Reservation reservation = findById(reservationId);
        validateIsTourReservation(reservation);
        validateStaffManageable(reservation);      // ← ADD

        Guide guide = reservation.getGuides().stream()
                .filter(g -> g.getGuideId().equals(guideId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Guide not found: " + guideId + " in reservation: " + reservationId));

        String guideName = guide.getFirstName() + " " + guide.getLastName();
        reservation.removeGuide(guide);
        Reservation savedReservation = reservationRepository.save(reservation);

        if (isActiveReservation(savedReservation)) {
            notificationPublisher.publish(
                    RabbitMQConfig.STAFF_UPDATED,
                    NotificationMessage.builder()
                            .targetUserId(savedReservation.getUser().getUserId())
                            .type(NotificationType.STAFF_UPDATED)
                            .reservationId(savedReservation.getReservationId())
                            .title("Personnel modifié")
                            .message("Le guide " + guideName + " a été retiré de votre réservation "
                                    + getReservationLabel(savedReservation) + ".")
                            .build()
            );
        }

        return "Guide deleted successfully";
    }

// ── Chauffeur management ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public ReservationResponse updateChauffeur(UUID reservationId, UUID chauffeurId, ChauffeurUpdateRequest request) {

        Reservation reservation = findById(reservationId);
        validateIsTourReservation(reservation);
        validateStaffManageable(reservation);      // ← ADD

        Chauffeur chauffeur = reservation.getChauffeurs().stream()
                .filter(c -> c.getChauffeurId().equals(chauffeurId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Chauffeur not found: " + chauffeurId + " in reservation: " + reservationId));

        if (request.getFirstName()   != null) chauffeur.setFirstName(request.getFirstName());
        if (request.getLastName()    != null) chauffeur.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) chauffeur.setPhoneNumber(request.getPhoneNumber());

        Reservation savedReservation = reservationRepository.save(reservation);

        if (isActiveReservation(savedReservation)) {
            notificationPublisher.publish(
                    RabbitMQConfig.STAFF_UPDATED,
                    NotificationMessage.builder()
                            .targetUserId(savedReservation.getUser().getUserId())
                            .type(NotificationType.STAFF_UPDATED)
                            .reservationId(savedReservation.getReservationId())
                            .title("Personnel modifié")
                            .message("Le chauffeur de votre réservation "
                                    + getReservationLabel(savedReservation) + " a été mis à jour.")
                            .build()
            );
        }

        return toEnrichedResponse(savedReservation);
    }

    @Override
    @Transactional
    public String deleteChauffeur(UUID reservationId, UUID chauffeurId) {

        Reservation reservation = findById(reservationId);
        validateIsTourReservation(reservation);
        validateStaffManageable(reservation);      // ← ADD

        Chauffeur chauffeur = reservation.getChauffeurs().stream()
                .filter(c -> c.getChauffeurId().equals(chauffeurId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Chauffeur not found: " + chauffeurId + " in reservation: " + reservationId));

        String chauffeurName = chauffeur.getFirstName() + " " + chauffeur.getLastName();
        reservation.removeChauffeur(chauffeur);
        Reservation savedReservation = reservationRepository.save(reservation);

        if (isActiveReservation(savedReservation)) {
            notificationPublisher.publish(
                    RabbitMQConfig.STAFF_UPDATED,
                    NotificationMessage.builder()
                            .targetUserId(savedReservation.getUser().getUserId())
                            .type(NotificationType.STAFF_UPDATED)
                            .reservationId(savedReservation.getReservationId())
                            .title("Personnel modifié")
                            .message("Le chauffeur " + chauffeurName + " a été retiré de votre réservation "
                                    + getReservationLabel(savedReservation) + ".")
                            .build()
            );
        }

        return "Chauffeur deleted successfully";
    }

// ── Private helpers ───────────────────────────────────────────────────────────

    private void validateIsTourReservation(Reservation reservation) {
        if (reservation.getReservationType() != ReservationType.TOURS) {
            throw new IllegalStateException(
                    "Staff (guides and chauffeurs) can only be managed on TOURS reservations. " +
                            "Current type: " + reservation.getReservationType());
        }
    }

    private void validateStaffManageable(Reservation reservation) {
        ReservationStatus status = reservation.getStatus();
        if (status == ReservationStatus.CANCELLED ||
                status == ReservationStatus.REJECTED  ||
                status == ReservationStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Le personnel ne peut pas être modifié pour une réservation "
                            + status.name().toLowerCase() + ".");
        }
    }

    private boolean isActiveReservation(Reservation reservation) {
        return reservation.getStatus() == ReservationStatus.CONFIRMED
                || reservation.getStatus() == ReservationStatus.CHECKED_IN;
    }

    private String getReservationLabel(Reservation reservation) {
        return reservation.getGroupName() != null
                ? "\"" + reservation.getGroupName() + "\""
                : "#" + reservation.getReservationId().toString().substring(0, 8).toUpperCase();
    }

    // Called once at reservation creation when initial payment currency != TND.
    // Converts every price field so the DB always holds values in the target currency.
    private void applyReservationCurrencyConversion(Reservation reservation, Currency targetCurrency) {
        if (targetCurrency == Currency.TND) {
            reservation.setCurrency(Currency.TND);
            return;
        }
        double rate = switch (targetCurrency) {
            case EUR -> currencyConfig.getEurRate();
            case USD -> currencyConfig.getUsdRate();
            case TND -> 1.0;
        };

        if (reservation.getTotalAmount() != null)
            reservation.setTotalAmount(r2(reservation.getTotalAmount() / rate));
        if (reservation.getTotalExtrasAmount() != null)
            reservation.setTotalExtrasAmount(r2(reservation.getTotalExtrasAmount() / rate));

        reservation.getTourTypes().forEach(tt -> {
            tt.setAdultPrice(r2(tt.getAdultPrice() / rate));
            tt.setChildPrice(r2(tt.getChildPrice() / rate));
        });

        reservation.getTours().forEach(tour -> {
            tour.setAdultPrice(r2(tour.getAdultPrice() / rate));
            tour.setChildPrice(r2(tour.getChildPrice() / rate));
            tour.setTotalPrice(r2(tour.getTotalPrice() / rate));
        });

        reservation.getExtras().forEach(extra -> {
            extra.setUnitPrice(r2(extra.getUnitPrice() / rate));
            extra.setTotalPrice(r2(extra.getTotalPrice() / rate));
        });

        reservation.setCurrency(targetCurrency);
    }

    private static double r2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getActiveReservations() {
        LocalDate today = LocalDate.now();

        return reservationRepository.findAllActive().stream()
                .sorted((a, b) -> {
                    LocalDate dateA = getRelevantDate(a);
                    LocalDate dateB = getRelevantDate(b);

                    boolean aIsPast = dateA != null && dateA.isBefore(today);
                    boolean bIsPast = dateB != null && dateB.isBefore(today);

                    if (aIsPast && !bIsPast) return 1;
                    if (!aIsPast && bIsPast) return -1;

                    if (dateA == null && dateB == null) return 0;
                    if (dateA == null) return 1;
                    if (dateB == null) return -1;

                    return dateA.compareTo(dateB);
                })
                .map(this::toEnrichedResponse)
                .toList();
    }

    private LocalDate getRelevantDate(Reservation reservation) {
        return switch (reservation.getReservationType()) {
            case HEBERGEMENT -> reservation.getCheckInDate();
            case EXTRAS -> reservation.getServiceDate();
            case TOURS -> {
                if (reservation.getTours() != null) {
                    LocalDate earliest = reservation.getTours().stream()
                            .flatMap(t -> t.getHebergements().stream())
                            .map(ReservationTourHebergement::getActivityDate)
                            .filter(d -> d != null)
                            .min(LocalDate::compareTo)
                            .orElse(null);
                    if (earliest != null) yield earliest;
                }
                yield reservation.getServiceDate();
            }
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getActiveReservationsByDate(LocalDate date) {
        LocalDate today = LocalDate.now();

        return reservationRepository.findAllActive(date).stream()
                .sorted((a, b) -> {
                    LocalDate dateA = getRelevantDate(a);
                    LocalDate dateB = getRelevantDate(b);

                    boolean aIsPast = dateA != null && dateA.isBefore(today);
                    boolean bIsPast = dateB != null && dateB.isBefore(today);

                    if (aIsPast && !bIsPast) return 1;
                    if (!aIsPast && bIsPast) return -1;

                    if (dateA == null && dateB == null) return 0;
                    if (dateA == null) return 1;
                    if (dateB == null) return -1;

                    return dateA.compareTo(dateB);
                })
                .map(this::toEnrichedResponse)
                .toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByDate(LocalDate date) {
        return reservationRepository.findAllByDate(date)
                .stream()
                .map(this::toEnrichedResponse)
                .toList();
    }
    //camping
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getCampingActiveReservations() {
        LocalDate today   = LocalDate.now();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        return reservationRepository.findCampingActive(today, cutoff)
                .stream()
                .map(this::toEnrichedResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getCampingActiveReservationsByDate(LocalDate date) {
        return reservationRepository.findCampingActiveByDate(date)
                .stream()
                .map(this::toEnrichedResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> searchCampingReservationsByName(String name) {
        LocalDate today = LocalDate.now();
        return reservationRepository.findCampingActiveByName(name, today)
                .stream()
                .map(this::toEnrichedResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getCampingReservationsByStatus(ReservationStatus status) {
        LocalDate today = LocalDate.now();
        return reservationRepository.findCampingActiveByStatus(status, today)
                .stream()
                .map(this::toEnrichedResponse)
                .toList();
    }

    private double getTimbreFiscal(Currency currency) {
        return switch (currency) {
            case TND -> 1.000;
            case EUR -> Math.round((1.0 / currencyConfig.getEurRate()) * 1000.0) / 1000.0;
            case USD -> Math.round((1.0 / currencyConfig.getUsdRate()) * 1000.0) / 1000.0;
        };
    }

    @Override
    @Transactional
    public ReservationResponse recalculateCurrency(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));

        // Prices on tour types / tours / extras are already in the reservation's currency.
        // Just recompute the aggregate totals from those stored values.
        switch (reservation.getReservationType()) {
            case HEBERGEMENT -> reservation.setTotalAmount(reservation.calculateTotalTourTypesAmount());
            case TOURS       -> reservation.setTotalAmount(reservation.calculateTotalToursAmount());
            case EXTRAS      -> { /* no main amount for pure-extras reservations */ }
        }
        reservation.setTotalExtrasAmount(reservation.calculateTotalExtrasAmount());

        reservationRepository.saveAndFlush(reservation);
        entityManager.detach(reservation);
        return toEnrichedResponse(findById(reservationId));
    }

    @Override
    @Transactional(readOnly = true)
    public CampingStatsResponse getCampingStats() {
        LocalDate today = LocalDate.now();

        // ── Stat 1: In Camp ───────────────────────────────────────
        List<Reservation> inCamp = reservationRepository.findCampingCheckedIn();
        int inCampAdults   = inCamp.stream().mapToInt(r -> r.getNumberOfAdults()   != null ? r.getNumberOfAdults()   : 0).sum();
        int inCampChildren = inCamp.stream().mapToInt(r -> r.getNumberOfChildren() != null ? r.getNumberOfChildren() : 0).sum();

        // ── Stat 2: Arriving Today ────────────────────────────────
        List<Reservation> arriving = reservationRepository.findCampingArrivingToday(today);
        int arrivingAdults   = arriving.stream().mapToInt(r -> r.getNumberOfAdults()   != null ? r.getNumberOfAdults()   : 0).sum();
        int arrivingChildren = arriving.stream().mapToInt(r -> r.getNumberOfChildren() != null ? r.getNumberOfChildren() : 0).sum();

        return CampingStatsResponse.builder()
                .inCampAdults(inCampAdults)
                .inCampChildren(inCampChildren)
                .inCampTotal(inCampAdults + inCampChildren)
                .arrivingTodayAdults(arrivingAdults)
                .arrivingTodayChildren(arrivingChildren)
                .arrivingTodayTotal(arrivingAdults + arrivingChildren)
                .build();
    }
}