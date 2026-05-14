package com.example.dem_login.model;

import java.time.LocalDateTime;

public class Customer {
    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String address;
    private String description;
    private CustomerStatus status;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    // enum xac dinh trang thai cua customer
    public enum CustomerStatus {
        ACTIVE,
        INACTIVE
    }

    public Customer() {
    }

    // getter vs setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDecription() {
        return description;
    }

    public void setDecription(String description) {
        this.description = description;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public void setStatus(CustomerStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public LocalDateTime getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(LocalDateTime updateDate) {
        this.updateDate = updateDate;
    }
}