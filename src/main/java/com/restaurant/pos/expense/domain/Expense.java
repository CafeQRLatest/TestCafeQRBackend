package com.restaurant.pos.expense.domain;

import com.restaurant.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Enterprise-grade Domain Entity for Expenses.
 * Dedicated to the stand-alone 'expenses' table.
 */
@Data
@Entity
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "expenses")
public class Expense extends BaseEntity {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "expense_no", nullable = false)
    private String expenseNo;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "terminal_id")
    private UUID terminalId;

    @Column(name = "expense_date", nullable = false)
    @Builder.Default
    private Instant expenseDate = Instant.now();

    @Builder.Default
    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "doc_status", length = 20)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "payment_status", length = 20)
    @Builder.Default
    private String paymentStatus = "PAID";

    @Column(name = "original_expense_id")
    private UUID originalExpenseId;

    @Column(name = "revision_number")
    @Builder.Default
    private Integer revisionNumber = 0;

    @Column(name = "currency_id")
    private UUID currencyId;

    @Builder.Default
    @Column(name = "isactive", length = 1)
    private String isactive = "Y";

    public boolean isActive() {
        return "Y".equals(this.isactive);
    }

    public void deactivate() {
        this.isactive = "N";
    }
}
