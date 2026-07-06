package com.camping.duneinsolite.dto.response;

import com.camping.duneinsolite.model.enums.TenteType;
import lombok.Data;
import java.util.UUID;

@Data
public class TourHebergementRepartitionResponse {
    private UUID repartitionId;
    private TenteType tenteType;
    private Integer numberOfTentes;
    private Integer capacityPerTente;
    private Integer totalPersonnes;
}
