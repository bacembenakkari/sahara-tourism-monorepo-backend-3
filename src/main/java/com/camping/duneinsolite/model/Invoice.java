package com.camping.duneinsolite.model;

import com.camping.duneinsolite.model.enums.Currency;
import com.camping.duneinsolite.model.enums.InvoiceStatus;
import com.camping.duneinsolite.model.enums.InvoiceType;
import com.camping.duneinsolite.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "invoice_id", updatable = false, nullable = false)
    private UUID invoiceId;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    private InvoiceType invoiceType;

    @Column(name = "invoice_date", nullable = false)
    @Builder.Default
    private LocalDate invoiceDate = LocalDate.now();

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Column(name = "paid_amount", nullable = false)
    @Builder.Default
    private Double paidAmount = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private Currency currency = Currency.TND;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "total_ht")
    private Double totalHt;

    @Column(name = "tva_rate")
    @Builder.Default
    private Double tvaRate = 7.0;

    @Column(name = "tva_amount")
    private Double tvaAmount;

    @Column(name = "timbre_fiscal")
    private Double timbreFiscal;

    @Column(name = "total_ttc")
    private Double totalTtc;

    // remainingAmount is computed, not stored
    @Transient
    public Double getRemainingAmount() {
        if (totalAmount == null) return 0.0;
        return totalAmount - (paidAmount != null ? paidAmount : 0.0);
    }

    // ──────────────────────────────────────────────
    // RELATIONS
    // ──────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    // Invoice is addressed to one user (CLIENT or PARTENAIRE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    // ──────────────────────────────────────────────
    // HELPER METHODS
    // ──────────────────────────────────────────────

    public void addItem(InvoiceItem item) {
        items.add(item);
        item.setInvoice(this);
    }

    public void removeItem(InvoiceItem item) {
        items.remove(item);
        item.setInvoice(null);
    }

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        transaction.setInvoice(this);
    }
}