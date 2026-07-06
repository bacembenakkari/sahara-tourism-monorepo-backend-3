package com.camping.duneinsolite.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservation_tour_hebergements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReservationTourHebergement {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "hebergement_id", updatable = false, nullable = false)
    private UUID hebergementId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_tour_id", nullable = false)
    private ReservationTour reservationTour;

    // Snapshot from TourType catalog at booking time
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration")
    private String duration;

    @Column(name = "number_of_nights")
    private Integer numberOfNights;

    @Column(name = "number_of_adults", nullable = false)
    private Integer numberOfAdults;

    @Column(name = "number_of_children", nullable = false)
    private Integer numberOfChildren;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    // No price fields — always complimentary

    @OneToMany(mappedBy = "hebergement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReservationTourHebergementRepartition> repartitions = new ArrayList<>();

    public void addRepartition(ReservationTourHebergementRepartition r) {
        repartitions.add(r);
        r.setHebergement(this);
    }
}
