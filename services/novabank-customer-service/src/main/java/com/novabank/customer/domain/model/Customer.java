package com.novabank.customer.domain.model;

import com.novabank.customer.domain.valueobject.identifier.CustomerId;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public class Customer {

    private final CustomerId id;
    private final UserId userId;
    private final String firstName;
    private final String lastName;
    private final LocalDate dateOfBirth;
    private final String phone;

    private CustomerStatus status;

    private final Instant createdAt;
    private Instant updatedAt;

    private Customer(
        CustomerId id,
        UserId userId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String phone,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "CustomerId cannot be null");
        this.userId = Objects.requireNonNull(userId, "UserId cannot be null");
        this.firstName = requireText(firstName, "First name cannot be blank");
        this.lastName = requireText(lastName, "Last name cannot be blank");
        this.dateOfBirth = Objects.requireNonNull(
            dateOfBirth,
            "Date of birth cannot be null"
        );
        this.phone = requireText(phone, "Phone cannot be blank");
        this.status = Objects.requireNonNull(
            status,
            "Customer status cannot be null"
        );
        this.createdAt = Objects.requireNonNull(
            createdAt,
            "Created at cannot be null"
        );
        this.updatedAt = Objects.requireNonNull(
            updatedAt,
            "Updated at cannot be null"
        );
    }

    public static Customer register(
        UserId userId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String phone
    ) {
        Instant now = Instant.now();

        return new Customer(
            CustomerId.random(),
            userId,
            firstName,
            lastName,
            dateOfBirth,
            phone,
            CustomerStatus.ACTIVE,
            now,
            now
        );
    }

    public static Customer restore(
        CustomerId id,
        UserId userId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String phone,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new Customer(
            id,
            userId,
            firstName,
            lastName,
            dateOfBirth,
            phone,
            status,
            createdAt,
            updatedAt
        );
    }

    public void activate() {
        this.status = CustomerStatus.ACTIVE;
        touch();
    }

    public void deactivate() {
        this.status = CustomerStatus.INACTIVE;
        touch();
    }

    public void block() {
        this.status = CustomerStatus.BLOCKED;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static String requireText(
        String value,
        String message
    ) {
        Objects.requireNonNull(value, message);

        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    public CustomerId id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public LocalDate dateOfBirth() {
        return dateOfBirth;
    }

    public String phone() {
        return phone;
    }

    public CustomerStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
