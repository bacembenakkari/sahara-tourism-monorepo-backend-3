package com.camping.duneinsolite.service.impl;

import com.camping.duneinsolite.config.CurrencyConfig;
import com.camping.duneinsolite.config.RabbitMQConfig;
import com.camping.duneinsolite.dto.message.NotificationMessage;
import com.camping.duneinsolite.dto.request.PaymentRequest;
import com.camping.duneinsolite.dto.response.PaymentResponse;
import com.camping.duneinsolite.dto.response.PaymentSummary;
import com.camping.duneinsolite.dto.response.TransactionResponse;
import com.camping.duneinsolite.mapper.TransactionMapper;
import com.camping.duneinsolite.model.Reservation;
import com.camping.duneinsolite.model.Transaction;
import com.camping.duneinsolite.model.enums.*;
import com.camping.duneinsolite.repository.ReservationRepository;
import com.camping.duneinsolite.repository.TransactionRepository;
import com.camping.duneinsolite.service.NotificationPublisher;
import com.camping.duneinsolite.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final TransactionRepository transactionRepository;
    private final ReservationRepository reservationRepository;
    private final TransactionMapper transactionMapper;
    private final NotificationPublisher notificationPublisher;
    private final CurrencyConfig currencyConfig;

    @Override
    public PaymentResponse recordPayment(UUID reservationId, PaymentRequest request) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));

        Currency requestedCurrency = request.getCurrency();

        // ── Step 1: Check if this is the first payment ────────────
        boolean isFirstPayment = transactionRepository
                .sumCompletedAmountByReservationId(reservationId) == 0.0;

        if (isFirstPayment) {
            // ── First payment: apply conversion if currency differs ──
            if (requestedCurrency != reservation.getCurrency()) {
                applyCurrencyConversion(reservation, requestedCurrency);
                reservationRepository.save(reservation);
            }
        } else {
            // ── Subsequent payment: currency must match reservation ──
            if (requestedCurrency != reservation.getCurrency()) {
                throw new RuntimeException(
                        "Currency mismatch. This reservation must be paid in "
                                + reservation.getCurrency().name()
                                + ". You provided: " + requestedCurrency.name());
            }
        }

        // ── Step 2: Validate amount <= remainingTotal ─────────────
        PaymentSummary current = computePaymentSummary(reservation);

        if (current.getPaymentStatus() == PaymentStatus.PAID) {
            throw new RuntimeException(
                    "This reservation is already fully paid. No further payments are required.");
        }

        if (request.getAmount() > current.getRemainingTotal()) {
            throw new RuntimeException(
                    "Payment amount (" + request.getAmount() + " " + requestedCurrency.name()
                            + ") exceeds the remaining balance ("
                            + current.getRemainingTotal() + " " + reservation.getCurrency().name() + ").");
        }

        // ── Step 3: Save transaction ──────────────────────────────
        Transaction transaction = buildTransaction(reservation, request);
        Transaction saved = transactionRepository.save(transaction);

        // ── Step 4: Recompute after save ──────────────────────────
        PaymentSummary summary = computePaymentSummary(reservation);

        publishPaymentReceivedInternal(reservation, request.getAmount());

        if (summary.getPaymentStatus() == PaymentStatus.PAID) {
            publishPaymentCompletedInternal(reservation);
        }

        TransactionResponse txResponse = transactionMapper.toResponse(saved);

        PaymentResponse response = new PaymentResponse();
        response.setTransactionId(txResponse.getTransactionId());
        response.setTransactionNumber(txResponse.getTransactionNumber());
        response.setAmount(txResponse.getAmount());
        response.setCurrency(txResponse.getCurrency());
        response.setPaymentMethod(txResponse.getPaymentMethod());
        response.setStatus(txResponse.getStatus());
        response.setTransactionDate(txResponse.getTransactionDate());
        response.setReservationId(reservationId);
        response.setPaymentSummary(summary);

        return response;
    }

    // ── Currency conversion ───────────────────────────────────────
    // Called once at first non-TND payment. Converts every stored price on
    // the reservation and all its child records (tour types, tours, extras).
    private void applyCurrencyConversion(Reservation reservation, Currency targetCurrency) {
        if (targetCurrency == Currency.TND) {
            reservation.setCurrency(Currency.TND);
            return;
        }
        double rate = switch (targetCurrency) {
            case EUR -> currencyConfig.getEurRate();
            case USD -> currencyConfig.getUsdRate();
            case TND -> 1.0;
        };

        if (reservation.getTotalAmount() != null)
            reservation.setTotalAmount(r2(reservation.getTotalAmount() / rate));
        if (reservation.getTotalExtrasAmount() != null)
            reservation.setTotalExtrasAmount(r2(reservation.getTotalExtrasAmount() / rate));

        reservation.getTourTypes().forEach(tt -> {
            tt.setAdultPrice(r2(tt.getAdultPrice() / rate));
            tt.setChildPrice(r2(tt.getChildPrice() / rate));
        });

        reservation.getTours().forEach(tour -> {
            tour.setAdultPrice(r2(tour.getAdultPrice() / rate));
            tour.setChildPrice(r2(tour.getChildPrice() / rate));
            tour.setTotalPrice(r2(tour.getTotalPrice() / rate));
        });

        reservation.getExtras().forEach(extra -> {
            extra.setUnitPrice(r2(extra.getUnitPrice() / rate));
            extra.setTotalPrice(r2(extra.getTotalPrice() / rate));
        });

        reservation.setCurrency(targetCurrency);
    }

    // ── Compute payment summary ───────────────────────────────────
    // All stored amounts are already in the reservation's currency — just read them.
    @Override
    @Transactional(readOnly = true)
    public PaymentSummary computePaymentSummary(Reservation reservation) {
        double main   = reservation.getTotalAmount()       != null ? reservation.getTotalAmount()       : 0.0;
        double extras = reservation.getTotalExtrasAmount() != null ? reservation.getTotalExtrasAmount() : 0.0;
        double total  = r2(main + extras);

        double totalPaid = transactionRepository
                .sumCompletedAmountByReservationId(reservation.getReservationId());

        double remainingMain;
        double remainingExtras;

        if (totalPaid >= main) {
            remainingMain   = 0.0;
            double overflow = totalPaid - main;
            remainingExtras = Math.max(0.0, r2(extras - overflow));
        } else {
            remainingMain   = r2(main - totalPaid);
            remainingExtras = extras;
        }

        double remainingTotal = r2(remainingMain + remainingExtras);

        PaymentStatus paymentStatus;
        if (totalPaid <= 0) {
            paymentStatus = PaymentStatus.UNPAID;
        } else if (remainingTotal > 0) {
            paymentStatus = PaymentStatus.PARTIALLY_PAID;
        } else {
            paymentStatus = PaymentStatus.PAID;
        }

        return PaymentSummary.builder()
                .originalMainAmount(main)
                .originalExtrasAmount(extras)
                .originalTotalAmount(total)
                .totalPaid(totalPaid)
                .remainingMainAmount(remainingMain)
                .remainingExtrasAmount(remainingExtras)
                .remainingTotal(remainingTotal)
                .paymentStatus(paymentStatus)
                .build();
    }

    private static double r2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ── Build transaction ─────────────────────────────────────────
    @Override
    public Transaction buildTransaction(Reservation reservation, PaymentRequest request) {
        Currency currency = request.getCurrency() != null
                ? request.getCurrency()
                : reservation.getCurrency();

        return Transaction.builder()
                .transactionNumber(generateTransactionNumber())
                .amount(request.getAmount())
                .currency(currency)
                .paymentMethod(request.getPaymentMethod())
                .status(TransactionStatus.COMPLETED)
                .reservation(reservation)
                .build();
    }

    // ── Notifications ─────────────────────────────────────────────
    @Override
    public void publishPaymentReceivedInternal(Reservation reservation, Double amount) {
        String amountFormatted = String.format("%.2f %s",
                amount, reservation.getCurrency().name());

        notificationPublisher.publish(
                RabbitMQConfig.PAYMENT_RECEIVED,
                NotificationMessage.builder()
                        .targetRoles(List.of(UserRole.ADMIN))
                        .type(NotificationType.PAYMENT_RECEIVED)
                        .reservationId(reservation.getReservationId())
                        .title("Paiement reçu")
                        .message("Le groupe \"" + reservation.getGroupName()
                                + "\" a effectué un paiement de " + amountFormatted + ".")
                        .build()
        );

        notificationPublisher.publish(
                RabbitMQConfig.PAYMENT_RECEIVED,
                NotificationMessage.builder()
                        .targetUserId(reservation.getUser().getUserId())
                        .type(NotificationType.PAYMENT_RECEIVED)
                        .reservationId(reservation.getReservationId())
                        .title("Paiement enregistré")
                        .message("Votre paiement de " + amountFormatted
                                + " pour la réservation du groupe \""
                                + reservation.getGroupName() + "\" a bien été enregistré.")
                        .build()
        );
    }

    @Override
    public void publishPaymentCompletedInternal(Reservation reservation) {
        notificationPublisher.publish(
                RabbitMQConfig.PAYMENT_COMPLETED,
                NotificationMessage.builder()
                        .targetRoles(List.of(UserRole.ADMIN))
                        .type(NotificationType.PAYMENT_COMPLETED)
                        .reservationId(reservation.getReservationId())
                        .title("Réservation entièrement payée")
                        .message("Le groupe \"" + reservation.getGroupName()
                                + "\" a réglé la totalité du montant dû.")
                        .build()
        );

        notificationPublisher.publish(
                RabbitMQConfig.PAYMENT_COMPLETED,
                NotificationMessage.builder()
                        .targetUserId(reservation.getUser().getUserId())
                        .type(NotificationType.PAYMENT_COMPLETED)
                        .reservationId(reservation.getReservationId())
                        .title("Paiement complet")
                        .message("Votre réservation pour le groupe \""
                                + reservation.getGroupName()
                                + "\" est entièrement réglée. Merci !")
                        .build()
        );
    }

    private String generateTransactionNumber() {
        long count = transactionRepository.count() + 1;
        return String.format("TXN-%05d", count);
    }
}