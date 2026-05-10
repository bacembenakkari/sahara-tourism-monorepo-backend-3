package com.camping.duneinsolite.dto.statistics;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsDTO {

    // Revenue KPIs
    private Double totalRevenue;
    private Double revenueGrowth;          // % vs previous period

    // Reservation KPIs
    private Long totalReservations;
    private Long confirmedReservations;
    private Long pendingReservations;
    private Long cancelledReservations;
    private Double reservationGrowth;      // % vs previous period

    // Direct passengers block
    private Double passengerDirectRevenue;
    private Long   passengerDirectCount;
    private Double passengerRevenuePercentage;

    // Period used for the query (30 or 90 days)
    private Integer period;
}
