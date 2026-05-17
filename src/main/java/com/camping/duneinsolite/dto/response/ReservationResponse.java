package com.camping.duneinsolite.dto.response;

import com.camping.duneinsolite.model.enums.Currency;
import com.camping.duneinsolite.model.enums.ReservationStatus;
import com.camping.duneinsolite.model.enums.ReservationType;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ReservationResponse {
    private UUID reservationId;
    private UUID userId;
    private String userName;
   // private String source;
    private ReservationType reservationType;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private LocalDate serviceDate;
    private String groupName;
    private String groupLeaderName;
    private Integer numberOfAdults;
    private Integer numberOfChildren;
    private ReservationStatus status;
    private String rejectionReason;
    private Double totalAmount;
    private Double totalExtrasAmount;
    private Currency currency;
    private String promoCode;
    private String demandeSpecial;
    private List<ReservationTourTypeResponse> tourTypes;
    private List<ReservationTourResponse> tours;
    private List<ParticipantResponse> participants;
    private List<ReservationExtraResponse> extras;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    // ── Payment — computed from transactions, never stored in DB ──
    // Injected manually in service after MapStruct mapping
    private PaymentSummary paymentSummary;

    // Full transaction history — each payment event with its date
    private List<TransactionResponse> transactions;
    private List<RepartitionResponse> repartitions;

    private SourceResponse source;
    private List<GuideResponse> guides;
    private List<ChauffeurResponse> chauffeurs;
}