package com.camping.duneinsolite.repository;

import com.camping.duneinsolite.model.Invoice;
import com.camping.duneinsolite.model.enums.InvoiceType;
import com.camping.duneinsolite.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByReservationReservationId(UUID reservationId);
    List<Invoice> findByUserUserId(UUID userId);
    List<Invoice> findByPaymentStatus(PaymentStatus paymentStatus);
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    long countByInvoiceType(InvoiceType invoiceType);
    List<Invoice> findByInvoiceTypeAndReservationReservationId(InvoiceType invoiceType, UUID reservationId);

    List<Invoice> findByInvoiceType(InvoiceType invoiceType);
}