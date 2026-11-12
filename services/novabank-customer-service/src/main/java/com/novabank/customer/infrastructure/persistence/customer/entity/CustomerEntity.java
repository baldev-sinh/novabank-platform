package com.novabank.customer.infrastructure.persistence.customer.entity;

import com.novabank.customer.domain.model.CustomerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** JPA mapping of {@code customers}. The schema is owned by Flyway; see db/migration. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customers")
public class CustomerEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "date_of_birth", nullable = false)
  private LocalDate dateOfBirth;

  @Column(name = "phone", nullable = false)
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private CustomerStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Incremented on every update; a stale update fails instead of overwriting newer data. */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  public static CustomerEntity of(
      UUID id,
      UUID userId,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String phone,
      CustomerStatus status,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    CustomerEntity entity = new CustomerEntity();
    entity.id = id;
    entity.userId = userId;
    entity.firstName = firstName;
    entity.lastName = lastName;
    entity.dateOfBirth = dateOfBirth;
    entity.phone = phone;
    entity.status = status;
    entity.createdAt = createdAt;
    entity.updatedAt = updatedAt;
    entity.version = version;
    return entity;
  }
}
