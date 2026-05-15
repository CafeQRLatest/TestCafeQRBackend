package com.restaurant.pos.sequence.domain;

/**
 * Defines the types of documents that can have custom numbering sequences.
 */
public enum DocumentType {
    SALE_ORDER,
    QUOTATION,
    DELIVERY_CHALLAN,
    CREDIT_NOTE,
    PURCHASE_ORDER,
    PURCHASE_BILL,
    DEBIT_NOTE,
    EXPENSE,
    CUSTOMER_INVOICE,
    VENDOR_BILL,
    EXPENSE_RECEIPT,
    INBOUND_PAYMENT,
    OUTBOUND_PAYMENT,
    PAYMENT_IN,
    PAYMENT_OUT,
    STOCK_ADJUSTMENT,
    STOCK_TRANSFER,
    PRODUCTION_ORDER,
    JOURNAL_ENTRY
}
