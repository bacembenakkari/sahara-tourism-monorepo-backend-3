package com.camping.duneinsolite.model;

import com.camping.duneinsolite.model.enums.TenteType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

@Entity
@Table(name = "reservation_tour_hebergement_repartitions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReservationTourHebergementRepartition {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "repartition_id", updatable = false, nullable = false)
    private UUID repartitionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hebergement_id", nullable = false)
    private ReservationTourHebergement hebergement;

    @Enumerated(EnumType.STRING)
    @Column(name = "tente_type", nullable = false)
    private TenteType tenteType;

    @Column(name = "number_of_tentes", nullable = false)
    private Integer numberOfTentes;

    @Transient
    public Integer getTotalPersonnes() {
        if (numberOfTentes == null || tenteType == null) return 0;
        return numberOfTentes * tenteType.getCapacity();
    }
}
