package com.novabank.customer.domain.model;

import com.novabank.customer.domain.exception.DomainException;
import com.novabank.customer.domain.valueobject.PhoneNumber;
import com.novabank.customer.domain.valueobject.identifier.CustomerId;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * A bank customer: the person behind a user account. One customer per user. Status changes go
 * through the methods below, which reject transitions the lifecycle doesn't allow (see {@link
 * CustomerStatus}).
 */
public final class Customer {

  /** Customers must be adults to open a profile. */
  public static final int MINIMUM_AGE = 18;

  public static final int MAX_NAME_LENGTH = 100;

  private final CustomerId id;
  private final UserId userId;
  private final String firstName;
  private final String lastName;
  private final LocalDate dateOfBirth;
  private final PhoneNumber phone;
  private CustomerStatus status;
  private final Instant createdAt;
  private Instant updatedAt;

  /** Optimistic-lock version; {@code null} until the customer is first stored. */
  private final Long version;

  private Customer(
      CustomerId id,
      UserId userId,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      PhoneNumber phone,
      CustomerStatus status,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    this.id = Objects.requireNonNull(id, "CustomerId cannot be null");
    this.userId = Objects.requireNonNull(userId, "UserId cannot be null");
    this.firstName = requireName(firstName, "First name");
    this.lastName = requireName(lastName, "Last name");
    this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "Date of birth cannot be null");
    this.phone = Objects.requireNonNull(phone, "Phone cannot be null");
    this.status = Objects.requireNonNull(status, "Customer status cannot be null");
    this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Updated at cannot be null");
    this.version = version;
  }

  /**
   * Registers a new customer, pending KYC.
   *
   * @throws DomainException if a name is blank or too long, or the person is under {@value
   *     #MINIMUM_AGE} or born in the future
   */
  public static Customer register(
      UserId userId,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      PhoneNumber phone,
      Clock clock) {
    Objects.requireNonNull(clock, "Clock cannot be null");
    Objects.requireNonNull(dateOfBirth, "Date of birth cannot be null");
    requireAdult(dateOfBirth, LocalDate.now(clock));

    Instant now = clock.instant();
    return new Customer(
        CustomerId.random(),
        userId,
        firstName,
        lastName,
        dateOfBirth,
        phone,
        CustomerStatus.PENDING_KYC,
        now,
        now,
        null);
  }

  /** Rebuilds a customer from storage without re-applying registration rules. */
  public static Customer restore(
      CustomerId id,
      UserId userId,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      PhoneNumber phone,
      CustomerStatus status,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    return new Customer(
        id, userId, firstName, lastName, dateOfBirth, phone, status, createdAt, updatedAt, version);
  }

  /** KYC passed, or a reactivation: PENDING_KYC or INACTIVE becomes ACTIVE. */
  public void activate(Clock clock) {
    requireStatus("activate", CustomerStatus.PENDING_KYC, CustomerStatus.INACTIVE);
    changeStatus(CustomerStatus.ACTIVE, clock);
  }

  /** Customer closes or pauses their profile: ACTIVE becomes INACTIVE. */
  public void deactivate(Clock clock) {
    requireStatus("deactivate", CustomerStatus.ACTIVE);
    changeStatus(CustomerStatus.INACTIVE, clock);
  }

  /** Bank blocks the customer, from any status except BLOCKED. */
  public void block(Clock clock) {
    if (status == CustomerStatus.BLOCKED) {
      throw new DomainException("Customer is already blocked.");
    }
    changeStatus(CustomerStatus.BLOCKED, clock);
  }

  /** Bank lifts a block: BLOCKED becomes ACTIVE. */
  public void unblock(Clock clock) {
    requireStatus("unblock", CustomerStatus.BLOCKED);
    changeStatus(CustomerStatus.ACTIVE, clock);
  }

  private void requireStatus(String action, CustomerStatus... allowed) {
    for (CustomerStatus candidate : allowed) {
      if (status == candidate) {
        return;
      }
    }
    throw new DomainException("Cannot " + action + " a customer in status " + status + ".");
  }

  private void changeStatus(CustomerStatus newStatus, Clock clock) {
    this.status = newStatus;
    this.updatedAt = Objects.requireNonNull(clock, "Clock cannot be null").instant();
  }

  private static String requireName(String value, String field) {
    Objects.requireNonNull(value, field + " cannot be null");
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new DomainException(field + " cannot be blank.");
    }
    if (trimmed.length() > MAX_NAME_LENGTH) {
      throw new DomainException(field + " must be at most " + MAX_NAME_LENGTH + " characters.");
    }
    return trimmed;
  }

  private static void requireAdult(LocalDate dateOfBirth, LocalDate today) {
    if (dateOfBirth.isAfter(today)) {
      throw new DomainException("Date of birth cannot be in the future.");
    }
    if (Period.between(dateOfBirth, today).getYears() < MINIMUM_AGE) {
      throw new DomainException("Customer must be at least " + MINIMUM_AGE + " years old.");
    }
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

  public PhoneNumber phone() {
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

  public Long version() {
    return version;
  }

  /** Entities are equal when they have the same id, whatever their current state. */
  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof Customer customer && id.equals(customer.id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /** No personal data (name, birth date, phone) here: toString ends up in logs. */
  @Override
  public String toString() {
    return "Customer[id=" + id.value() + ", status=" + status + "]";
  }
}
