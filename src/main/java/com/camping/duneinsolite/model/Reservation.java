package com.camping.duneinsolite.model;

import com.camping.duneinsolite.model.enums.Currency;
import com.camping.duneinsolite.model.enums.ReservationStatus;
import com.camping.duneinsolite.model.enums.ReservationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Reservation {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "reservation_id", updatable = false, nullable = false)
    private UUID reservationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_type", nullable = false)
    private ReservationType reservationType;

    // Nullable — only required for HEBERGEMENT type
    @Column(name = "check_in_date")
    private LocalDate checkInDate;

    // Nullable — only required for HEBERGEMENT type
    @Column(name = "check_out_date")
    private LocalDate checkOutDate;

    // Nullable — only required for EXTRAS type
    @Column(name = "service_date")
    private LocalDate serviceDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "group_name")
    private String groupName;

    @Column(name = "group_leader_name")
    private String groupLeaderName;

    @Column(name = "number_of_adults", nullable = false)
    @Builder.Default
    private Integer numberOfAdults = 0;

    @Column(name = "number_of_children", nullable = false)
    @Builder.Default
    private Integer numberOfChildren = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // Null for EXTRAS type — stores TourType or Tour total only
    @Column(name = "total_amount")
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3)
    @Builder.Default
    private Currency currency = Currency.TND;

    @Column(name = "promo_code")
    private String promoCode;

    @Column(name = "total_extras_amount")
    @Builder.Default
    private Double totalExtrasAmount = 0.0;

    @Column(name = "demande_special", columnDefinition = "TEXT")
    private String demandeSpecial;

    // ── HEBERGEMENT — TourTypes ───────────────────────────────────
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReservationTourType> tourTypes = new ArrayList<>();

    // ── TOURS — ReservationTour ───────────────────────────────────
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReservationTour> tours = new ArrayList<>();

    // ── Participants ──────────────────────────────────────────────
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Participant> participants = new ArrayList<>();

    // ── Extras ────────────────────────────────────────────────────
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReservationExtra> extras = new ArrayList<>();

    // ── Invoices ──────────────────────────────────────────────────
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Invoice> invoices = new ArrayList<>();

    // ── Transactions ──────────────────────────────────────────────
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    // ── Source (OneToOne) ─────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_ref_id")
    private Source sourceRef;

    // ── Guides ────────────────────────────────────────────────────
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Guide> guides = new ArrayList<>();

    // ── Chauffeurs ────────────────────────────────────────────────
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Chauffeur> chauffeurs = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Helper methods ────────────────────────────────────────────

    public void addTourType(ReservationTourType tourType) {
        tourTypes.add(tourType);
        tourType.setReservation(this);
    }

    public void removeTourType(ReservationTourType tourType) {
        tourTypes.remove(tourType);
        tourType.setReservation(null);
    }

    public void addTour(ReservationTour tour) {
        tours.add(tour);
        tour.setReservation(this);
    }

    public void removeTour(ReservationTour tour) {
        tours.remove(tour);
        tour.setReservation(null);
    }

    public void addParticipant(Participant participant) {
        participants.add(participant);
        participant.setReservation(this);
    }

    public void removeParticipant(Participant participant) {
        participants.remove(participant);
        participant.setReservation(null);
    }

    public void addExtra(ReservationExtra extra) {
        extras.add(extra);
        extra.setReservation(this);
    }

    public void removeExtra(ReservationExtra extra) {
        extras.remove(extra);
        extra.setReservation(null);
    }

    public void addInvoice(Invoice invoice) {
        invoices.add(invoice);
        invoice.setReservation(this);
    }

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        transaction.setReservation(this);
    }

    public Double calculateTotalExtrasAmount() {
        return extras.stream()
                .mapToDouble(ReservationExtra::getTotalPrice)
                .sum();
    }

    public Double calculateTotalTourTypesAmount() {
        return tourTypes.stream()
                .mapToDouble(ReservationTourType::getTotalPrice)
                .sum();
    }

    public Double calculateTotalToursAmount() {
        return tours.stream()
                .mapToDouble(ReservationTour::getTotalPrice)
                .sum();
    }
    public void addGuide(Guide guide) {
        guides.add(guide);
        guide.setReservation(this);
    }

    public void removeGuide(Guide guide) {
        guides.remove(guide);
        guide.setReservation(null);
    }

    public void addChauffeur(Chauffeur chauffeur) {
        chauffeurs.add(chauffeur);
        chauffeur.setReservation(this);
    }

    public void removeChauffeur(Chauffeur chauffeur) {
        chauffeurs.remove(chauffeur);
        chauffeur.setReservation(null);
    }
}