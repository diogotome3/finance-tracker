package com.financetracker.category;

import com.financetracker.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.financetracker.common.domain.TransactionType;

@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING) // Specify that the enum should be stored as a string in the database, stores 'EXPENSE' not 0/1
    @Column(name = "type", nullable = false, length = 10)
    private TransactionType type;

    @Column(name = "description", length = 255)
    private String description;

    @JdbcTypeCode(SqlTypes.CHAR)                   // org.hibernate.annotations / org.hibernate.type
    @Column(name = "color", length = 7)
    private String color;

    protected Category() {
        // Default constructor for JPA
    }

    public Category(Long userId, String name, TransactionType type) {
        this.userId = userId;
        this.name = name;
        this.type = type;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

}
