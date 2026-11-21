package com.financetracker.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.financetracker.category.Category;
import com.financetracker.common.domain.TransactionType;
import com.financetracker.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity {

    // Plain column, not @ManyToOne AppUser: we never navigate from a transaction to its user
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // LAZY: the category is only fetched when getCategory() is actually used
    // optional = false: Hibernate knows it's never null (matches category_id NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // Stored here (duplicated from the category) so the composite FK can check it
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private TransactionType type;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    protected Transaction() {
        // Default constructor for JPA
    }

    public Transaction(Long userId, Category category, TransactionType type,
                       String description, BigDecimal amount, LocalDate transactionDate) {
        this.userId = userId;
        this.category = category;
        this.type = type;
        this.description = description;
        this.amount = amount;
        this.transactionDate = transactionDate;
    }

    public Long getUserId() {
        return userId;
    }

    public Category getCategory() {
        return category;
    }

    public TransactionType getType() {
        return type;
    }

    /** Category and type change together: the database rejects a category of another type */
    public void changeCategory(Category category, TransactionType type) {
        this.category = category;
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
