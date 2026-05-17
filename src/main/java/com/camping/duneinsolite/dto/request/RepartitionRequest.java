package com.camping.duneinsolite.dto.request;

import com.camping.duneinsolite.model.enums.TenteType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RepartitionRequest {

    @NotNull(message = "Tente type is required")
    private TenteType tenteType;

    @NotNull(message = "Number of tentes is required")
    @Min(value = 1, message = "Number of tentes must be at least 1")
    private Integer numberOfTentes;
}