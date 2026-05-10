package com.camping.duneinsolite.repository;

import com.camping.duneinsolite.model.Reservation;
import com.camping.duneinsolite.model.User;
import com.camping.duneinsolite.model.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    List<Reservation> findByUserUserId(UUID userId);
    List<Reservation> findByStatus(ReservationStatus status);
    List<Reservation> findByUserOrderByCreatedAtDesc(User user);
    @Query("SELECT r FROM Reservation r WHERE LOWER(r.user.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Reservation> searchByUserName(@Param("name") String name);

    // finds by date across all 3 reservation types correctly
    List<Reservation> findByCheckInDateOrServiceDate(LocalDate checkInDate, LocalDate serviceDate);

    @Query("SELECT r FROM Reservation r WHERE r.status != com.camping.duneinsolite.model.enums.ReservationStatus.COMPLETED")
    List<Reservation> findAllActive();
    @Query("""
    SELECT r FROM Reservation r
    WHERE r.status != com.camping.duneinsolite.model.enums.ReservationStatus.COMPLETED
    ORDER BY 
      CASE 
        WHEN (r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
              AND r.checkInDate >= :today)
          OR (r.reservationType != com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
              AND r.serviceDate >= :today)
        THEN 0
        ELSE 1
      END ASC,
      CASE 
        WHEN r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT 
        THEN r.checkInDate
        ELSE r.serviceDate
      END ASC
""")
    List<Reservation> findAllActive(@Param("today") LocalDate today);


    @Query("""
    SELECT r FROM Reservation r
    WHERE (r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
           AND r.checkInDate = :date)
       OR (r.reservationType != com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
           AND r.serviceDate = :date)
    ORDER BY r.createdAt DESC
""")
    List<Reservation> findAllByDate(@Param("date") LocalDate date);


    // camping
    @Query("""
    SELECT r FROM Reservation r
    WHERE r.reservationType IN (
        com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT,
        com.camping.duneinsolite.model.enums.ReservationType.EXTRAS
    )
    AND (
        (
            r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
            AND r.status IN (
                com.camping.duneinsolite.model.enums.ReservationStatus.PENDING,
                com.camping.duneinsolite.model.enums.ReservationStatus.CONFIRMED,
                com.camping.duneinsolite.model.enums.ReservationStatus.CHECKED_IN
            )
            AND r.checkInDate >= :today
        )
        OR
        (
            r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
            AND r.status = com.camping.duneinsolite.model.enums.ReservationStatus.COMPLETED
            AND r.completedAt >= :cutoff
        )
        OR
        (
            r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.EXTRAS
            AND r.status IN (
                com.camping.duneinsolite.model.enums.ReservationStatus.PENDING,
                com.camping.duneinsolite.model.enums.ReservationStatus.CONFIRMED,
                com.camping.duneinsolite.model.enums.ReservationStatus.CHECKED_IN
            )
            AND r.serviceDate >= :today
        )
        OR
        (
            r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.EXTRAS
            AND r.status = com.camping.duneinsolite.model.enums.ReservationStatus.COMPLETED
            AND r.completedAt >= :cutoff
        )
    )
    ORDER BY
        CASE
            WHEN r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
            THEN r.checkInDate
            ELSE r.serviceDate
        END ASC
""")
    List<Reservation> findCampingActive(
            @Param("today") LocalDate today,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Query("""
    SELECT r FROM Reservation r
    WHERE r.status IN (
        com.camping.duneinsolite.model.enums.ReservationStatus.CONFIRMED,
        com.camping.duneinsolite.model.enums.ReservationStatus.CHECKED_IN
    )
    AND r.reservationType IN (
        com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT,
        com.camping.duneinsolite.model.enums.ReservationType.EXTRAS
    )
    AND LOWER(r.user.name) LIKE LOWER(CONCAT('%', :name, '%'))
    AND (
        (r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
         AND r.checkInDate >= :today)
        OR
        (r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.EXTRAS
         AND r.serviceDate >= :today)
    )
    ORDER BY r.createdAt DESC
""")
    List<Reservation> findCampingActiveByName(@Param("name") String name, @Param("today") LocalDate today);

    @Query("""
    SELECT r FROM Reservation r
    WHERE r.status = :status
    AND r.reservationType IN (
        com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT,
        com.camping.duneinsolite.model.enums.ReservationType.EXTRAS
    )
    AND (
        (r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
         AND r.checkInDate >= :today)
        OR
        (r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.EXTRAS
         AND r.serviceDate >= :today)
    )
    ORDER BY
      CASE
        WHEN r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
        THEN r.checkInDate
        ELSE r.serviceDate
      END ASC
""")
    List<Reservation> findCampingActiveByStatus(@Param("status") ReservationStatus status, @Param("today") LocalDate today);

    // by-date — all statuses, HEBERGEMENT + EXTRAS only, exact date match
    @Query("""
    SELECT r FROM Reservation r
    WHERE r.reservationType IN (
        com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT,
        com.camping.duneinsolite.model.enums.ReservationType.EXTRAS
    )
    AND (
        (r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.HEBERGEMENT
         AND r.checkInDate = :date)
        OR
        (r.reservationType = com.camping.duneinsolite.model.enums.ReservationType.EXTRAS
         AND r.serviceDate = :date)
    )
    ORDER BY r.createdAt DESC
""")
    List<Reservation> findCampingActiveByDate(@Param("date") LocalDate date);
}
