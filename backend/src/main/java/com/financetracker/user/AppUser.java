package com.financetracker.user;

import com.financetracker.common.persistence.BaseEntity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import jakarta.persistence.Entity;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    protected AppUser() {
        // Default constructor for Hibernate
    }

    public AppUser(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

}
