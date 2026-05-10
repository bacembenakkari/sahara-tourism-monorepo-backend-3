package com.camping.duneinsolite.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

@Entity
@Table(name = "tour_types")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TourType {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "tour_type_id", updatable = false, nullable = false)
    private UUID tourTypeId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration")
    private String duration;

    // Prices for direct passengers
    @Column(name = "passenger_adult_price", nullable = false)
    private Double passengerAdultPrice;

    @Column(name = "passenger_child_price", nullable = false)
    private Double passengerChildPrice;

    // Prices for partner bookings
    @Column(name = "partner_adult_price", nullable = false)
    private Double partnerAdultPrice;

    @Column(name = "partner_child_price", nullable = false)
    private Double partnerChildPrice;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

}