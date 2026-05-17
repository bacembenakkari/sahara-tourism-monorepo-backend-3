package com.camping.duneinsolite.dto.request;

import com.camping.duneinsolite.model.enums.Currency;
import com.camping.duneinsolite.model.enums.ReservationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class ReservationRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Source is required")
    private UUID sourceId;

    @NotNull(message = "Reservation type is required")
    private ReservationType reservationType;

    // Nullable — required only for HEBERGEMENT (validated in service)
    private LocalDate checkInDate;

    // Nullable — required only for HEBERGEMENT (validated in service)
    private LocalDate checkOutDate;

    // Nullable — required only for EXTRAS (validated in service)
    private LocalDate serviceDate;

    private String groupName;
    private String groupLeaderName;

    @NotNull(message = "Number of adults is required")
    @Min(value = 0, message = "Number of adults cannot be negative")
    private Integer numberOfAdults;

    @NotNull(message = "Number of children is required")
    @Min(value = 0, message = "Number of children cannot be negative")
    private Integer numberOfChildren;

   // private Currency currency;
    private String promoCode;
    private String demandeSpecial;

    // Required for HEBERGEMENT — validated in service
    private List<TourTypeSelectionRequest> tourTypes;

    // Required for TOURS (exactly 1) — validated in service
    private List<TourSelectionRequest> tours;

    private List<ParticipantRequest> participants;
    private List<ReservationExtraRequest> extras;
    private List<RepartitionRequest> repartitions;

    // ── NEW — Optional initial payment at reservation creation time ──
    // If provided → a Transaction is created immediately after save
    // If null     → reservation is created with paymentStatus = UNPAID
    @Valid
    private PaymentRequest initialPayment;
}