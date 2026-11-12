package com.novabank.auth.infrastructure.persistence.user.entity;

import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.UserStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
// The schema (incl. the unique email constraint) is owned by Flyway; see db/migration.
@Table(name = "users")
public class UserEntity {

  @Id private UUID id;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private UserStatus status;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id", nullable = false))
  @Column(name = "role_name", nullable = false)
  @Enumerated(EnumType.STRING)
  private Set<RoleName> roles = EnumSet.noneOf(RoleName.class);

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Incremented on every update; a stale update fails instead of overwriting newer data. */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  public static UserEntity from(
      UUID id,
      String email,
      String passwordHash,
      UserStatus status,
      Set<RoleName> roles,
      Instant createdAt,
      Instant updatedAt,
      Long version) {
    UserEntity entity = new UserEntity();
    entity.id = id;
    entity.email = email;
    entity.passwordHash = passwordHash;
    entity.status = status;

    Objects.requireNonNull(roles, "Roles cannot be null");
    entity.roles = EnumSet.copyOf(roles);

    entity.createdAt = createdAt;
    entity.updatedAt = updatedAt;
    entity.version = version;
    return entity;
  }
}
