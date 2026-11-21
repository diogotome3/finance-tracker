package com.financetracker.budget;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.financetracker.common.persistence.BaseEntity;

@Entity
@Table(name = "monthly_budgets")
public class MonthlyBudget extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // int in Java, SMALLINT in the DB: without @JdbcTypeCode Hibernate validation expects INTEGER
    @JdbcTypeCode(SqlTypes.SMALLINT) // org.hibernate.annotations / org.hibernate.type
    @Column(name = "budget_year", nullable = false)
    private int year;

    @JdbcTypeCode(SqlTypes.SMALLINT) // org.hibernate.annotations / org.hibernate.type
    @Column(name = "budget_month", nullable = false)
    private int month;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected MonthlyBudget() {
        // Default constructor for JPA
    }

    // Year before month, like the table and findByUserIdAndYearAndMonth: two ints side by side are easy to swap
    public MonthlyBudget(Long userId, int year, int month, BigDecimal amount) {
        this.userId = userId;
        this.year = year;
        this.month = month;
        this.amount = amount;
    }

    public Long getUserId() {
        return userId;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    // Only the amount can change: a budget's year/month identify it (PUT /api/budgets/{year}/{month})
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
