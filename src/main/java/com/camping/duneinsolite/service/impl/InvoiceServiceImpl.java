package com.camping.duneinsolite.service.impl;

import com.camping.duneinsolite.dto.request.InvoiceRequest;
import com.camping.duneinsolite.dto.response.InvoiceItemResponse;
import com.camping.duneinsolite.dto.response.InvoiceResponse;
import com.camping.duneinsolite.mapper.InvoiceMapper;
import com.camping.duneinsolite.model.*;
import com.camping.duneinsolite.model.enums.InvoiceStatus;
import com.camping.duneinsolite.model.enums.InvoiceType;
import com.camping.duneinsolite.model.enums.PaymentStatus;
import com.camping.duneinsolite.model.enums.ReservationType;
import com.camping.duneinsolite.repository.*;
import com.camping.duneinsolite.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final InvoiceMapper invoiceMapper;

    @Override
    public InvoiceResponse createInvoice(InvoiceRequest request) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + request.getReservationId()));

        // ── Take user from reservation, not from request ──
        User user = reservation.getUser();

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber())
                .invoiceType(request.getInvoiceType())
                .dueDate(request.getDueDate())
                .totalAmount(request.getTotalAmount())
                .paidAmount(0.0)
                .status(InvoiceStatus.DRAFT)
                .paymentStatus(PaymentStatus.UNPAID)
                .reservation(reservation)
                .user(user)
                .build();

        request.getItems().forEach(itemReq -> {
            InvoiceItem item = InvoiceItem.builder()
                    .description(itemReq.getDescription())
                    .itemType(itemReq.getItemType())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .lineNumber(itemReq.getLineNumber())
                    .build();
            invoice.addItem(item);
        });

        return invoiceMapper.toResponse(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(UUID invoiceId) {
        return invoiceMapper.toResponse(findById(invoiceId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getAllInvoices() {
        return invoiceRepository.findAll().stream().map(invoiceMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByReservation(UUID reservationId) {
        return invoiceRepository.findByReservationReservationId(reservationId).stream()
                .map(invoiceMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByUser(UUID userId) {
        return invoiceRepository.findByUserUserId(userId).stream()
                .map(invoiceMapper::toResponse).toList();
    }

    @Override
    public InvoiceResponse updateInvoice(UUID invoiceId, InvoiceRequest request) {
        Invoice invoice = findById(invoiceId);
        invoice.setInvoiceType(request.getInvoiceType());
        invoice.setDueDate(request.getDueDate());
        invoice.setTotalAmount(request.getTotalAmount());
        return invoiceMapper.toResponse(invoiceRepository.save(invoice));
    }

    @Override
    public void deleteInvoice(UUID invoiceId) {
        invoiceRepository.delete(findById(invoiceId));
    }

    // Auto-generates invoice number like INV-00001
    private String generateInvoiceNumber() {
        long count = invoiceRepository.count() + 1;
        return String.format("INV-%05d", count);
    }

    private Invoice findById(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
    }


    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getAllFactures() {
        return invoiceRepository.findByInvoiceType(InvoiceType.STANDARD)
                .stream()
                .map(this::toFactureResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getFacturesByReservation(UUID reservationId) {
        return invoiceRepository
                .findByInvoiceTypeAndReservationReservationId(InvoiceType.STANDARD, reservationId)
                .stream()
                .map(this::toFactureResponse)
                .toList();
    }

    // Compute items dynamically from reservation so all factures show per-type lines
    private InvoiceResponse toFactureResponse(Invoice invoice) {
        InvoiceResponse response = invoiceMapper.toResponse(invoice);
        response.setItems(computeItemsFromReservation(invoice.getReservation()));
        return response;
    }

    private List<InvoiceItemResponse> computeItemsFromReservation(Reservation r) {
        List<InvoiceItemResponse> items = new ArrayList<>();
        if (r == null) return items;
        int line = 1;

        // HEBERGEMENT: one line per tour type — qty = people, unitPrice = totalStay ÷ people
        if (r.getReservationType() == ReservationType.HEBERGEMENT
                && r.getTourTypes() != null && !r.getTourTypes().isEmpty()) {
            for (ReservationTourType tt : r.getTourTypes()) {
                int qty = Optional.ofNullable(tt.getNumberOfAdults()).orElse(0)
                        + Optional.ofNullable(tt.getNumberOfChildren()).orElse(0);
                if (qty == 0) qty = 1;
                double unitP = Math.round((tt.getTotalPrice() / qty) * 1000.0) / 1000.0;
                items.add(buildItem(line++, tt.getName(), "HEBERGEMENT", qty, unitP));
            }
        // TOURS: one line per tour — qty = people, unitPrice = totalTour ÷ people
        } else if (r.getReservationType() == ReservationType.TOURS
                && r.getTours() != null && !r.getTours().isEmpty()) {
            for (ReservationTour t : r.getTours()) {
                int qty = Optional.ofNullable(t.getNumberOfAdults()).orElse(0)
                        + Optional.ofNullable(t.getNumberOfChildren()).orElse(0);
                if (qty == 0) qty = 1;
                double unitP = t.getTotalPrice() != null
                        ? Math.round((t.getTotalPrice() / qty) * 1000.0) / 1000.0 : 0.0;
                items.add(buildItem(line++, t.getName(), "TOURS", qty, unitP));
            }
        }

        // EXTRAS: one line per active extra
        if (r.getExtras() != null) {
            for (ReservationExtra extra : r.getExtras()) {
                if (Boolean.TRUE.equals(extra.getIsActive())) {
                    int qty  = Optional.ofNullable(extra.getQuantity()).orElse(1);
                    double unitP = Optional.ofNullable(extra.getUnitPrice()).orElse(0.0);
                    items.add(buildItem(line++, extra.getName(), "EXTRA", qty, unitP));
                }
            }
        }

        return items;
    }

    private InvoiceItemResponse buildItem(int lineNumber, String description,
                                          String itemType, int quantity, double unitPrice) {
        InvoiceItemResponse item = new InvoiceItemResponse();
        item.setLineNumber(lineNumber);
        item.setDescription(description);
        item.setItemType(itemType);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setTotalPrice(Math.round(quantity * unitPrice * 1000.0) / 1000.0);
        return item;
    }

}