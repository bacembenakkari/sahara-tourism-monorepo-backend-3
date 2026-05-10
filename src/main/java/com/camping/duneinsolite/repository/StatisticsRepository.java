package com.camping.duneinsolite.repository;

import com.camping.duneinsolite.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StatisticsRepository extends JpaRepository<Transaction, UUID> {



    /**
     * Total revenue from COMPLETED transactions within a date range.
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0.0)
        FROM Transaction t
        WHERE t.status = com.camping.duneinsolite.model.enums.TransactionStatus.COMPLETED
          AND t.transactionDate >= :since
          AND t.transactionDate < :until
    """)
    Double getTotalRevenue(
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until
    );

    /**
     * Revenue grouped by month for a given year (all COMPLETED transactions).
     * Returns [month (1-12), totalRevenue].
     */
    @Query("""
        SELECT MONTH(t.transactionDate), COALESCE(SUM(t.amount), 0.0)
        FROM Transaction t
        WHERE t.status = com.camping.duneinsolite.model.enums.TransactionStatus.COMPLETED
          AND YEAR(t.transactionDate) = :year
        GROUP BY MONTH(t.transactionDate)
        ORDER BY MONTH(t.transactionDate)
    """)
    List<Object[]> getMonthlyRevenueTrend(@Param("year") int year);

    /**
     * Revenue by partner (PARTENAIRE role users) within a date range.
     * Returns [partnerName, totalRevenue].
     */
    @Query("""
        SELECT u.name, COALESCE(SUM(t.amount), 0.0)
        FROM Transaction t
        JOIN t.reservation r
        JOIN r.user u
        WHERE u.role = com.camping.duneinsolite.model.enums.UserRole.PARTENAIRE
          AND t.status = com.camping.duneinsolite.model.enums.TransactionStatus.COMPLETED
          AND t.transactionDate >= :since
          AND t.transactionDate < :until
        GROUP BY u.name
        ORDER BY SUM(t.amount) DESC
    """)
    List<Object[]> getRevenueByPartner(
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until
    );

    /**
     * Total revenue coming only from direct passengers (CLIENT role) within a date range.
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0.0)
        FROM Transaction t
        JOIN t.reservation r
        JOIN r.user u
        WHERE u.role = com.camping.duneinsolite.model.enums.UserRole.CLIENT
          AND t.status = com.camping.duneinsolite.model.enums.TransactionStatus.COMPLETED
          AND t.transactionDate >= :since
          AND t.transactionDate < :until
    """)
    Double getDirectPassengerRevenue(
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until
    );

    /**
     * Monthly revenue for direct passengers (CLIENT) for a given year.
     * Returns [month (1-12), totalRevenue].
     */
    @Query("""
        SELECT MONTH(t.transactionDate), COALESCE(SUM(t.amount), 0.0)
        FROM Transaction t
        JOIN t.reservation r
        JOIN r.user u
        WHERE u.role = com.camping.duneinsolite.model.enums.UserRole.CLIENT
          AND t.status = com.camping.duneinsolite.model.enums.TransactionStatus.COMPLETED
          AND YEAR(t.transactionDate) = :year
        GROUP BY MONTH(t.transactionDate)
        ORDER BY MONTH(t.transactionDate)
    """)
    List<Object[]> getMonthlyPassengerRevenueTrend(@Param("year") int year);

    // ─────────────────────────────────────────────────────────────────────────
    // RESERVATIONS — based on Reservation entity
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Total non-deleted reservations within a date range.
     */
    @Query("""
        SELECT COUNT(r)
        FROM Reservation r
        WHERE r.createdAt >= :since
          AND r.createdAt < :until
          AND r.deletedAt IS NULL
    """)
    Long getTotalReservations(
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until
    );

    /**
     * Count reservations by status within a date range.
     * Returns [ReservationStatus (String), count].
     */
    @Query("""
        SELECT r.status, COUNT(r)
        FROM Reservation r
        WHERE r.createdAt >= :since
          AND r.createdAt < :until
          AND r.deletedAt IS NULL
        GROUP BY r.status
    """)
    List<Object[]> getReservationCountByStatus(
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until
    );

    /**
     * Monthly reservation counts for a given year.
     * Returns [month (1-12), count].
     */
    @Query("""
        SELECT MONTH(r.createdAt), COUNT(r)
        FROM Reservation r
        WHERE YEAR(r.createdAt) = :year
          AND r.deletedAt IS NULL
        GROUP BY MONTH(r.createdAt)
        ORDER BY MONTH(r.createdAt)
    """)
    List<Object[]> getMonthlyReservationCount(@Param("year") int year);

    /**
     * Count of direct passenger (CLIENT) reservations within a date range.
     */
    @Query("""
        SELECT COUNT(r)
        FROM Reservation r
        JOIN r.user u
        WHERE u.role = com.camping.duneinsolite.model.enums.UserRole.CLIENT
          AND r.createdAt >= :since
          AND r.createdAt < :until
          AND r.deletedAt IS NULL
    """)
    Long getDirectPassengerReservationCount(
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until
    );

    /**
     * Monthly reservation counts for direct passengers (CLIENT) for a given year.
     * Returns [month (1-12), count].
     */
    @Query("""
        SELECT MONTH(r.createdAt), COUNT(r)
        FROM Reservation r
        JOIN r.user u
        WHERE u.role = com.camping.duneinsolite.model.enums.UserRole.CLIENT
          AND YEAR(r.createdAt) = :year
          AND r.deletedAt IS NULL
        GROUP BY MONTH(r.createdAt)
        ORDER BY MONTH(r.createdAt)
    """)
    List<Object[]> getMonthlyPassengerReservationCount(@Param("year") int year);

    // ─────────────────────────────────────────────────────────────────────────
    // SOURCES
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reservation count grouped by source name within a date range.
     * Returns [sourceName, count].
     * Reservations with no source are labeled "Direct".
     */
    @Query("""
        SELECT COALESCE(s.name, 'Direct'), COUNT(r)
        FROM Reservation r
        LEFT JOIN r.sourceRef s
        WHERE r.createdAt >= :since
          AND r.createdAt < :until
          AND r.deletedAt IS NULL
        GROUP BY s.name
        ORDER BY COUNT(r) DESC
    """)
    List<Object[]> getReservationsBySource(
            @Param("since") LocalDateTime since,
            @Param("until") LocalDateTime until
    );
}