package com.camping.duneinsolite.dto.request;

import com.camping.duneinsolite.model.enums.InvoiceType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class InvoiceRequest {

    @NotNull(message = "Reservation ID is required")
    private UUID reservationId;


    private UUID userId;

    @NotNull(message = "Invoice type is required")
    private InvoiceType invoiceType;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    @NotNull(message = "Total amount is required")
    @Positive(message = "Total amount must be positive")
    private Double totalAmount;

    @NotEmpty(message = "At least one invoice item is required")
    private List<InvoiceItemRequest> items;
}