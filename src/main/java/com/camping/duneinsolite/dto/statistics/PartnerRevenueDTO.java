package com.camping.duneinsolite.dto.statistics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PartnerRevenueDTO {

    private List<PartnerEntryDTO> partners;
    private Double                totalPartnerRevenue;
    private Double                partnerRevenuePercentage; // % of grand total

    @Data
    @Builder
    public static class PartnerEntryDTO {
        private String name;
        private Double revenue;
        private Double percentage; // % of total partner revenue
    }
}
