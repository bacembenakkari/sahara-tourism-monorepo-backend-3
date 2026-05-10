package com.camping.duneinsolite.dto.statistics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RevenueDistributionDTO {

    private Double partnerRevenue;
    private Double directRevenue;
    private Double partnerPercentage;
    private Double directPercentage;
    private Double totalRevenue;
}