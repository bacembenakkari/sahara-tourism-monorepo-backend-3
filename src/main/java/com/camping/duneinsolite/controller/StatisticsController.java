package com.camping.duneinsolite.controller;

import com.camping.duneinsolite.dto.statistics.*;
import com.camping.duneinsolite.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing all analytics endpoints consumed by the
 * Angular StatistiquesComponent dashboard.
 *
 * Base path : /api/admin/statistics
 * All routes require ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {

    private static final int DEFAULT_PERIOD = 30;
    private static final int MAX_PERIOD     = 365;

    private final StatisticsService statisticsService;

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/admin/statistics/dashboard?period=30
    //
    // Feeds: all four KPI cards (revenue, reservations, pending, direct clients)
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsDTO> getDashboard(
            @RequestParam(defaultValue = "30") int period
    ) {
        return ResponseEntity.ok(
                statisticsService.getDashboardStats(sanitizePeriod(period))
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/admin/statistics/monthly-trend
    //
    // Feeds: the main bar chart (revenue + reservations, 12-month view,
    //        current calendar year). Period param is ignored intentionally —
    //        the chart always shows the full year; period only affects KPI cards.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/monthly-trend")
    public ResponseEntity<MonthlyTrendDTO> getMonthlyTrend() {
        return ResponseEntity.ok(statisticsService.getMonthlyTrend());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/admin/statistics/partner-revenue?period=30
    //
    // Feeds: horizontal bar chart + partner ranking list
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/partner-revenue")
    public ResponseEntity<PartnerRevenueDTO> getPartnerRevenue(
            @RequestParam(defaultValue = "30") int period
    ) {
        return ResponseEntity.ok(
                statisticsService.getPartnerRevenue(sanitizePeriod(period))
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/admin/statistics/sources?period=30
    //
    // Feeds: doughnut chart + detailed sources table at the bottom
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/sources")
    public ResponseEntity<SourceStatsDTO> getSources(
            @RequestParam(defaultValue = "30") int period
    ) {
        return ResponseEntity.ok(
                statisticsService.getSourceStats(sanitizePeriod(period))
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/admin/statistics/revenue-distribution?period=30
    //
    // Feeds: pie chart (Partenaires vs Directs)
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/revenue-distribution")
    public ResponseEntity<RevenueDistributionDTO> getRevenueDistribution(
            @RequestParam(defaultValue = "30") int period
    ) {
        return ResponseEntity.ok(
                statisticsService.getRevenueDistribution(sanitizePeriod(period))
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/admin/statistics/passenger-trend
    //
    // Feeds: mini sparkline in the "Passagers Directs" highlight card
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/passenger-trend")
    public ResponseEntity<PassengerTrendDTO> getPassengerTrend() {
        return ResponseEntity.ok(statisticsService.getPassengerTrend());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE — guard against absurd period values
    // ─────────────────────────────────────────────────────────────────────────
    private int sanitizePeriod(int period) {
        if (period <= 0)         return DEFAULT_PERIOD;
        if (period > MAX_PERIOD) return MAX_PERIOD;
        return period;
    }
}