package com.camping.duneinsolite.dto.response;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class TourHebergementResponse {
    private UUID hebergementId;
    private String name;
    private String description;
    private String duration;
    private Integer numberOfNights;
    private Integer numberOfAdults;
    private Integer numberOfChildren;
    private LocalDate activityDate;
    private List<TourHebergementRepartitionResponse> repartitions;
}
