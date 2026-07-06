package com.camping.duneinsolite.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class TourSelectionRequest {

    @NotNull(message = "Tour ID is required")
    private UUID tourId;

    // Optional hebergement activities included in this tour (always free)
    private List<TourHebergementRequest> hebergements;
}