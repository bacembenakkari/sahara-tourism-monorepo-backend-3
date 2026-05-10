package com.camping.duneinsolite.service;


import com.camping.duneinsolite.dto.request.InvoiceRequest;
import com.camping.duneinsolite.dto.response.InvoiceResponse;
import java.util.List;
import java.util.UUID;

public interface InvoiceService {
    InvoiceResponse createInvoice(InvoiceRequest request);
    InvoiceResponse getInvoiceById(UUID invoiceId);
    List<InvoiceResponse> getAllInvoices();
    List<InvoiceResponse> getInvoicesByReservation(UUID reservationId);
    List<InvoiceResponse> getInvoicesByUser(UUID userId);
    InvoiceResponse updateInvoice(UUID invoiceId, InvoiceRequest request);
    void deleteInvoice(UUID invoiceId);

    List<InvoiceResponse> getAllFactures();
    List<InvoiceResponse> getFacturesByReservation(UUID reservationId);
}
