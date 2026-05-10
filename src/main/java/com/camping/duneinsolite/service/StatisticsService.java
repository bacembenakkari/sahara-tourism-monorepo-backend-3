package com.camping.duneinsolite.service;

import com.camping.duneinsolite.dto.statistics.*;
import com.camping.duneinsolite.model.enums.ReservationStatus;
import com.camping.duneinsolite.repository.StatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private static final List<String> MONTH_LABELS =
            List.of("Jan", "Fév", "Mar", "Avr", "Mai", "Jun",
                    "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc");

    private final StatisticsRepository statisticsRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. DASHBOARD — single endpoint, all KPI cards
    // ─────────────────────────────────────────────────────────────────────────

    public DashboardStatsDTO getDashboardStats(int period) {

        LocalDateTime now        = LocalDateTime.now();
        LocalDateTime periodStart = now.minusDays(period);
        LocalDateTime prevStart  = periodStart.minusDays(period); // previous window for growth %

        // ── Revenue ──────────────────────────────────────────────────────────
        double currentRevenue  = statisticsRepository.getTotalRevenue(periodStart, now);
        double previousRevenue = statisticsRepository.getTotalRevenue(prevStart, periodStart);
        double revenueGrowth   = calculateGrowthPercentage(previousRevenue, currentRevenue);

        // ── Reservations by status ────────────────────────────────────────────
        List<Object[]> statusRows = statisticsRepository
                .getReservationCountByStatus(periodStart, now);

        long totalReservations    = 0L;
        long confirmed            = 0L;
        long pending              = 0L;
        long cancelled            = 0L;

        for (Object[] row : statusRows) {
            ReservationStatus status = (ReservationStatus) row[0];
            long count = (Long) row[1];
            totalReservations += count;

            switch (status) {
                case CONFIRMED, CHECKED_IN, COMPLETED -> confirmed += count;
                case PENDING                          -> pending   += count;
                case CANCELLED, REJECTED              -> cancelled += count;
            }
        }

        // Reservation growth vs previous period
        long prevTotalReservations = statisticsRepository
                .getTotalReservations(prevStart, periodStart);
        double reservationGrowth = calculateGrowthPercentage(
                (double) prevTotalReservations, (double) totalReservations);

        // ── Direct passengers ─────────────────────────────────────────────────
        double directRevenue = statisticsRepository
                .getDirectPassengerRevenue(periodStart, now);
        long directCount = statisticsRepository
                .getDirectPassengerReservationCount(periodStart, now);

        double directPercentage = currentRevenue > 0
                ? round2((directRevenue / currentRevenue) * 100)
                : 0.0;

        return DashboardStatsDTO.builder()
                .totalRevenue(round2(currentRevenue))
                .revenueGrowth(round2(revenueGrowth))
                .totalReservations(totalReservations)
                .confirmedReservations(confirmed)
                .pendingReservations(pending)
                .cancelledReservations(cancelled)
                .reservationGrowth(round2(reservationGrowth))
                .passengerDirectRevenue(round2(directRevenue))
                .passengerDirectCount(directCount)
                .passengerRevenuePercentage(directPercentage)
                .period(period)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. MONTHLY TREND — bar chart (always current calendar year)
    // ─────────────────────────────────────────────────────────────────────────

    public MonthlyTrendDTO getMonthlyTrend() {

        int currentYear = Year.now().getValue();

        // Revenue — keyed by month number (1–12)
        Map<Integer, Double> revenueByMonth = toDoubleMap(
                statisticsRepository.getMonthlyRevenueTrend(currentYear));

        // Reservation count — keyed by month number (1–12)
        Map<Integer, Long> reservationsByMonth = toLongMap(
                statisticsRepository.getMonthlyReservationCount(currentYear));

        List<Double> revenueList      = new ArrayList<>();
        List<Long>   reservationsList = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            revenueList.add(round2(revenueByMonth.getOrDefault(m, 0.0)));
            reservationsList.add(reservationsByMonth.getOrDefault(m, 0L));
        }

        return MonthlyTrendDTO.builder()
                .labels(MONTH_LABELS)
                .revenue(revenueList)
                .reservations(reservationsList)
                .year(currentYear)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PARTNER REVENUE — horizontal bar chart + partner list
    // ─────────────────────────────────────────────────────────────────────────

    public PartnerRevenueDTO getPartnerRevenue(int period) {

        LocalDateTime now         = LocalDateTime.now();
        LocalDateTime periodStart = now.minusDays(period);

        List<Object[]> rows = statisticsRepository
                .getRevenueByPartner(periodStart, now);

        double totalPartnerRevenue = rows.stream()
                .mapToDouble(r -> ((Number) r[1]).doubleValue())
                .sum();

        // Grand total (partner + direct) for the overall percentage
        double grandTotal = statisticsRepository.getTotalRevenue(periodStart, now);

        List<PartnerRevenueDTO.PartnerEntryDTO> partners = new ArrayList<>();

        for (Object[] row : rows) {
            String partnerName = (String) row[0];
            double revenue     = ((Number) row[1]).doubleValue();
            double pct         = totalPartnerRevenue > 0
                    ? round2((revenue / totalPartnerRevenue) * 100)
                    : 0.0;

            partners.add(PartnerRevenueDTO.PartnerEntryDTO.builder()
                    .name(partnerName)
                    .revenue(round2(revenue))
                    .percentage(pct)
                    .build());
        }

        double partnerGlobalPct = grandTotal > 0
                ? round2((totalPartnerRevenue / grandTotal) * 100)
                : 0.0;

        return PartnerRevenueDTO.builder()
                .partners(partners)
                .totalPartnerRevenue(round2(totalPartnerRevenue))
                .partnerRevenuePercentage(partnerGlobalPct)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. SOURCE DISTRIBUTION — doughnut chart + detailed table
    // ─────────────────────────────────────────────────────────────────────────

    public SourceStatsDTO getSourceStats(int period) {

        LocalDateTime now         = LocalDateTime.now();
        LocalDateTime periodStart = now.minusDays(period);

        List<Object[]> rows = statisticsRepository
                .getReservationsBySource(periodStart, now);

        long totalReservations = rows.stream()
                .mapToLong(r -> ((Number) r[1]).longValue())
                .sum();

        List<SourceStatsDTO.SourceEntryDTO> sources = new ArrayList<>();

        for (Object[] row : rows) {
            String sourceName = (String) row[0];
            long   count      = ((Number) row[1]).longValue();
            double pct        = totalReservations > 0
                    ? round2(((double) count / totalReservations) * 100)
                    : 0.0;

            sources.add(SourceStatsDTO.SourceEntryDTO.builder()
                    .source(sourceName)
                    .count(count)
                    .percentage(pct)
                    .build());
        }

        return SourceStatsDTO.builder()
                .sources(sources)
                .totalReservations(totalReservations)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. REVENUE DISTRIBUTION — pie chart (Partner vs Direct)
    // ─────────────────────────────────────────────────────────────────────────

    public RevenueDistributionDTO getRevenueDistribution(int period) {

        LocalDateTime now         = LocalDateTime.now();
        LocalDateTime periodStart = now.minusDays(period);

        double directRevenue  = statisticsRepository
                .getDirectPassengerRevenue(periodStart, now);
        double grandTotal     = statisticsRepository
                .getTotalRevenue(periodStart, now);
        double partnerRevenue = grandTotal - directRevenue;

        double partnerPct = grandTotal > 0 ? round2((partnerRevenue / grandTotal) * 100) : 0.0;
        double directPct  = grandTotal > 0 ? round2((directRevenue  / grandTotal) * 100) : 0.0;

        return RevenueDistributionDTO.builder()
                .partnerRevenue(round2(partnerRevenue))
                .directRevenue(round2(directRevenue))
                .partnerPercentage(partnerPct)
                .directPercentage(directPct)
                .totalRevenue(round2(grandTotal))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. PASSENGER TREND — mini sparkline for direct passengers
    // ─────────────────────────────────────────────────────────────────────────

    public PassengerTrendDTO getPassengerTrend() {

        int currentYear = Year.now().getValue();

        Map<Integer, Double> revenueByMonth = toDoubleMap(
                statisticsRepository.getMonthlyPassengerRevenueTrend(currentYear));

        Map<Integer, Long> reservationsByMonth = toLongMap(
                statisticsRepository.getMonthlyPassengerReservationCount(currentYear));

        List<Double> revenueList      = new ArrayList<>();
        List<Long>   reservationsList = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            revenueList.add(round2(revenueByMonth.getOrDefault(m, 0.0)));
            reservationsList.add(reservationsByMonth.getOrDefault(m, 0L));
        }

        double totalRevenue = revenueList.stream().mapToDouble(Double::doubleValue).sum();
        long   totalCount   = reservationsList.stream().mapToLong(Long::longValue).sum();

        return PassengerTrendDTO.builder()
                .labels(MONTH_LABELS)
                .revenue(revenueList)
                .reservations(reservationsList)
                .totalRevenue(round2(totalRevenue))
                .totalCount(totalCount)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculate percentage growth: ((current - previous) / previous) * 100.
     * Returns 0 when the previous value is 0 (avoids division by zero).
     */
    private double calculateGrowthPercentage(double previous, double current) {
        if (previous == 0) return current > 0 ? 100.0 : 0.0;
        return ((current - previous) / previous) * 100;
    }

    /**
     * Convert Object[]{monthInt, Double} rows into a Map<month, value>.
     */
    private Map<Integer, Double> toDoubleMap(List<Object[]> rows) {
        Map<Integer, Double> map = new HashMap<>();
        for (Object[] row : rows) {
            int    month = ((Number) row[0]).intValue();
            double value = ((Number) row[1]).doubleValue();
            map.put(month, value);
        }
        return map;
    }

    /**
     * Convert Object[]{monthInt, Long} rows into a Map<month, count>.
     */
    private Map<Integer, Long> toLongMap(List<Object[]> rows) {
        Map<Integer, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            int  month = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            map.put(month, count);
        }
        return map;
    }

    /** Round a double to 2 decimal places. */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}