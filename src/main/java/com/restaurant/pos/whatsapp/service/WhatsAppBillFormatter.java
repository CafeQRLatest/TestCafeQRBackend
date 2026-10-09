package com.restaurant.pos.whatsapp.service;

import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.domain.Organization;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class WhatsAppBillFormatter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    /**
     * Formats Order, Invoice, and Store details into the concrete industry standard WhatsApp Digital Tax Invoice.
     */
    public String formatBillMessage(Order order, Invoice invoice, Organization org, Client client) {
        if (order == null) return "";

        StringBuilder sb = new StringBuilder();

        String storeName = getStoreName(org, client);
        String currency = getCurrencySymbol(client);

        // 1. Header
        sb.append("🧾 *TAX INVOICE — ").append(storeName.toUpperCase()).append("*\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        // Customer Greeting
        String customerName = order.getCustomerName();
        if (customerName != null && !customerName.isBlank() && !isGenericGuest(customerName)) {
            sb.append("Hi *").append(customerName.trim()).append("*, thank you for dining with us! 😊\n\n");
        } else {
            sb.append("Thank you for dining with us! 😊\n\n");
        }

        // Bill Meta Info
        String invNo = (invoice != null && invoice.getInvoiceNo() != null) ? invoice.getInvoiceNo() : order.getOrderNo();
        sb.append("*Inv No:* #").append(invNo);
        if (order.getOrderNo() != null && !order.getOrderNo().equals(invNo)) {
            sb.append("  |  *Order:* #").append(order.getOrderNo());
        }
        sb.append("\n");

        LocalDateTime date = (invoice != null && invoice.getInvoiceDate() != null) 
                ? invoice.getInvoiceDate() 
                : (order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now());
        sb.append("*Date:* ").append(date.format(DATE_FMT)).append("\n");

        // Order Type & Table
        String fulfillment = formatFulfillment(order);
        if (!fulfillment.isBlank()) {
            sb.append("*Type:* ").append(fulfillment).append("\n");
        }

        sb.append("\n*Items Ordered:*\n");

        // 2. Line Items
        List<OrderLine> lines = order.getLines();
        if (lines != null && !lines.isEmpty()) {
            for (OrderLine line : lines) {
                if (line == null) continue;
                String prodName = line.getProductName() != null ? line.getProductName() : "Item";
                String note = line.getDescription();
                BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE;
                BigDecimal lineTotal = line.getLineTotal() != null ? line.getLineTotal() : BigDecimal.ZERO;

                sb.append(" • ").append(formatQty(qty)).append("x ").append(prodName);
                if (note != null && !note.isBlank()) {
                    sb.append(" (").append(note.trim()).append(")");
                }
                sb.append(" — ").append(currency).append(" ").append(fmt(lineTotal)).append("\n");
            }
        } else {
            sb.append(" • Order Items\n");
        }

        sb.append("────────────────────────────\n");

        // 3. Totals Breakdown
        BigDecimal subtotal = order.getGrossAmount() != null ? order.getGrossAmount() : order.getGrandTotal();
        if (subtotal != null) {
            sb.append("*Subtotal:* ").append(currency).append(" ").append(fmt(subtotal)).append("\n");
        }

        BigDecimal discount = order.getTotalDiscountAmount();
        if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("*Discount:* -").append(currency).append(" ").append(fmt(discount)).append("\n");
        }

        BigDecimal tax = order.getTotalTaxAmount();
        if (tax != null && tax.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("*Taxes / GST:* ").append(currency).append(" ").append(fmt(tax)).append("\n");
        }

        BigDecimal roundOff = order.getRoundOffAmount();
        if (roundOff != null && roundOff.compareTo(BigDecimal.ZERO) != 0) {
            String sign = roundOff.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
            sb.append("*Round Off:* ").append(sign).append(currency).append(" ").append(fmt(roundOff)).append("\n");
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        // 4. Grand Total & Payment Mode
        BigDecimal grandTotal = order.getGrandTotal() != null ? order.getGrandTotal() : BigDecimal.ZERO;
        sb.append("*TOTAL PAID:* *").append(currency).append(" ").append(fmt(grandTotal)).append("*\n");

        String payMethod = order.getReference() != null && !order.getReference().isBlank() 
                ? order.getReference().toUpperCase() 
                : "PAID";
        sb.append("*Payment Mode:* ").append(payMethod);
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            sb.append(" (PAID)");
        }
        sb.append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // 5. Store Details & Compliance Footer
        String address = (org != null && org.getAddress() != null && !org.getAddress().isBlank()) 
                ? org.getAddress() 
                : (client != null ? client.getAddress() : null);
        String phone = (org != null && org.getPhone() != null && !org.getPhone().isBlank()) 
                ? org.getPhone() 
                : (client != null ? client.getPhone() : null);

        if (address != null && !address.isBlank()) {
            sb.append("📍 *").append(address.trim()).append("*\n");
        }
        if (phone != null && !phone.isBlank()) {
            sb.append("📞 Contact: ").append(phone.trim()).append("\n");
        }

        // GSTIN & FSSAI
        String gstin = (org != null && org.getGstin() != null && !org.getGstin().isBlank()) 
                ? org.getGstin() 
                : (client != null ? client.getGstNumber() : null);
        String fssai = (client != null && client.getFssaiNumber() != null && !client.getFssaiNumber().isBlank()) 
                ? client.getFssaiNumber() 
                : null;

        if (gstin != null && !gstin.isBlank()) {
            sb.append("GSTIN: ").append(gstin.trim());
            if (fssai != null && !fssai.isBlank()) {
                sb.append("  |  FSSAI: ").append(fssai.trim());
            }
            sb.append("\n");
        } else if (fssai != null && !fssai.isBlank()) {
            sb.append("FSSAI: ").append(fssai.trim()).append("\n");
        }

        // Feedback / Google Review link
        String mapsUrl = (org != null && org.getGoogleMapsUrl() != null && !org.getGoogleMapsUrl().isBlank())
                ? org.getGoogleMapsUrl()
                : (client != null ? client.getGoogleMapsUrl() : null);
        if (mapsUrl != null && !mapsUrl.isBlank()) {
            sb.append("\n⭐ Enjoyed your food? Rate us here:\n").append(mapsUrl.trim()).append("\n");
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return sb.toString();
    }

    /**
     * Normalizes phone number into international WhatsApp format (e.g. 919847920009).
     */
    public String normalizePhoneNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) return null;
        String digits = rawPhone.replaceAll("[^0-9]", "");
        if (digits.length() < 10) return null;

        // If 10 digits (Standard Indian Mobile Number without prefix), prepend 91
        if (digits.length() == 10) {
            digits = "91" + digits;
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = "91" + digits.substring(1);
        }
        return digits;
    }

    private String getStoreName(Organization org, Client client) {
        if (org != null && org.getName() != null && !org.getName().isBlank()) {
            return org.getName().trim();
        }
        if (client != null && client.getName() != null && !client.getName().isBlank()) {
            return client.getName().trim();
        }
        return "Restaurant";
    }

    private String getCurrencySymbol(Client client) {
        if (client != null && client.getCurrency() != null && !client.getCurrency().isBlank()) {
            String c = client.getCurrency().trim();
            if ("INR".equalsIgnoreCase(c) || "₹".equals(c)) return "₹";
            if ("USD".equalsIgnoreCase(c) || "$".equals(c)) return "$";
            if ("EUR".equalsIgnoreCase(c) || "€".equals(c)) return "€";
            if ("GBP".equalsIgnoreCase(c) || "£".equals(c)) return "£";
            if ("AED".equalsIgnoreCase(c)) return "AED";
            if ("SAR".equalsIgnoreCase(c)) return "SAR";
            return c;
        }
        return "₹";
    }

    private String formatFulfillment(Order order) {
        if (order.getTableNumber() != null && !order.getTableNumber().isBlank()) {
            return "Dine-In (Table " + order.getTableNumber() + ")";
        }
        if (order.getFulfillmentType() != null && !order.getFulfillmentType().isBlank()) {
            String ft = order.getFulfillmentType().toUpperCase();
            if (ft.contains("TAKEAWAY")) return "Takeaway";
            if (ft.contains("DELIVERY")) return "Delivery";
            if (ft.contains("DINE")) return "Dine-In";
            return order.getFulfillmentType();
        }
        return "";
    }

    private String fmt(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatQty(BigDecimal qty) {
        if (qty == null) return "1";
        if (qty.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            return String.valueOf(qty.intValue());
        }
        return qty.stripTrailingZeros().toPlainString();
    }

    private boolean isGenericGuest(String name) {
        String lower = name.toLowerCase().trim();
        return lower.equals("guest") || lower.equals("walk-in") || lower.equals("walk in") || lower.equals("walk-in guest");
    }
}
