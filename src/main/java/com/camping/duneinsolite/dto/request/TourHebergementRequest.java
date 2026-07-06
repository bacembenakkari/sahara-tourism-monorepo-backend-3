package com.camping.duneinsolite.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class TourHebergementRequest {

    @NotNull(message = "Tour type ID is required")
    private UUID tourTypeId;

    @NotNull(message = "Activity date is required")
    private LocalDate activityDate;

    @Min(value = 1, message = "Number of nights must be at least 1")
    private Integer numberOfNights;

    @NotNull
    @Min(value = 0)
    private Integer numberOfAdults;

    @NotNull
    @Min(value = 0)
    private Integer numberOfChildren;

    private List<TourHebergementRepartitionRequest> repartitions;
}
