package com.novabank.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.customer.domain.exception.DomainException;
import com.novabank.customer.domain.valueobject.PhoneNumber;
import com.novabank.customer.domain.valueobject.identifier.CustomerId;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CustomerTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Clock LATER = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);

  private static final UserId USER_ID = new UserId(UUID.randomUUID());
  private static final LocalDate ADULT_BIRTH_DATE = LocalDate.of(2001, 1, 15);
  private static final PhoneNumber PHONE = PhoneNumber.of("+919876543210");

  @Nested
  class Registration {

    @Test
    @DisplayName("A new customer is pending KYC, with timestamps from the clock")
    void registersPendingKyc() {
      Customer customer = register();

      assertThat(customer.id()).isNotNull();
      assertThat(customer.userId()).isEqualTo(USER_ID);
      assertThat(customer.firstName()).isEqualTo("Baldev");
      assertThat(customer.lastName()).isEqualTo("Parmar");
      assertThat(customer.dateOfBirth()).isEqualTo(ADULT_BIRTH_DATE);
      assertThat(customer.phone()).isEqualTo(PHONE);
      assertThat(customer.status()).isEqualTo(CustomerStatus.PENDING_KYC);
      assertThat(customer.createdAt()).isEqualTo(NOW);
      assertThat(customer.updatedAt()).isEqualTo(NOW);
      assertThat(customer.version()).isNull();
    }

    @Test
    @DisplayName("Names are trimmed")
    void trimsNames() {
      Customer customer =
          Customer.register(USER_ID, "  Baldev ", " Parmar ", ADULT_BIRTH_DATE, PHONE, CLOCK);

      assertThat(customer.firstName()).isEqualTo("Baldev");
      assertThat(customer.lastName()).isEqualTo("Parmar");
    }

    @Test
    @DisplayName("Each registration gets a new id")
    void generatesIds() {
      assertThat(register().id()).isNotEqualTo(register().id());
    }

    @Test
    @DisplayName("Rejects blank and too-long names")
    void rejectsInvalidNames() {
      assertThatThrownBy(
              () -> Customer.register(USER_ID, " ", "Parmar", ADULT_BIRTH_DATE, PHONE, CLOCK))
          .isInstanceOf(DomainException.class)
          .hasMessage("First name cannot be blank.");
      assertThatThrownBy(() ->
              Customer.register(USER_ID, "Baldev", "x".repeat(101), ADULT_BIRTH_DATE, PHONE, CLOCK))
          .isInstanceOf(DomainException.class)
          .hasMessage("Last name must be at most 100 characters.");
    }

    @Test
    @DisplayName("Rejects people under 18, and accepts the 18th birthday itself")
    void requiresAdults() {
      LocalDate eighteenToday = LocalDate.of(2008, 11, 12);
      LocalDate eighteenTomorrow = LocalDate.of(2008, 11, 13);

      assertThat(Customer.register(USER_ID, "A", "B", eighteenToday, PHONE, CLOCK)).isNotNull();
      assertThatThrownBy(() -> Customer.register(USER_ID, "A", "B", eighteenTomorrow, PHONE, CLOCK))
          .isInstanceOf(DomainException.class)
          .hasMessage("Customer must be at least 18 years old.");
    }

    @Test
    @DisplayName("Rejects a birth date in the future")
    void rejectsFutureBirthDate() {
      assertThatThrownBy(
              () -> Customer.register(USER_ID, "A", "B", LocalDate.of(2030, 1, 1), PHONE, CLOCK))
          .isInstanceOf(DomainException.class)
          .hasMessage("Date of birth cannot be in the future.");
    }

    @Test
    @DisplayName("Rejects missing required values")
    void rejectsNulls() {
      assertThatThrownBy(() -> Customer.register(null, "A", "B", ADULT_BIRTH_DATE, PHONE, CLOCK))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Customer.register(USER_ID, "A", "B", null, PHONE, CLOCK))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Customer.register(USER_ID, "A", "B", ADULT_BIRTH_DATE, null, CLOCK))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class Lifecycle {

    @Test
    @DisplayName("PENDING_KYC → ACTIVE → INACTIVE → ACTIVE, updating the timestamp")
    void normalLifecycle() {
      Customer customer = register();

      customer.activate(LATER);
      assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
      assertThat(customer.updatedAt()).isEqualTo(NOW.plusSeconds(60));

      customer.deactivate(CLOCK);
      assertThat(customer.status()).isEqualTo(CustomerStatus.INACTIVE);

      customer.activate(CLOCK);
      assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(
        value = CustomerStatus.class,
        names = {"PENDING_KYC", "ACTIVE", "INACTIVE"})
    @DisplayName("Any non-blocked customer can be blocked, then unblocked to ACTIVE")
    void blockAndUnblock(CustomerStatus from) {
      Customer customer = restore(from);

      customer.block(CLOCK);
      assertThat(customer.status()).isEqualTo(CustomerStatus.BLOCKED);

      customer.unblock(CLOCK);
      assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("Rejects transitions the lifecycle doesn't allow")
    void rejectsInvalidTransitions() {
      assertThatThrownBy(() -> restore(CustomerStatus.ACTIVE).activate(CLOCK))
          .isInstanceOf(DomainException.class)
          .hasMessage("Cannot activate a customer in status ACTIVE.");
      assertThatThrownBy(() -> restore(CustomerStatus.BLOCKED).activate(CLOCK))
          .isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> restore(CustomerStatus.PENDING_KYC).deactivate(CLOCK))
          .isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> restore(CustomerStatus.BLOCKED).block(CLOCK))
          .isInstanceOf(DomainException.class)
          .hasMessage("Customer is already blocked.");
      assertThatThrownBy(() -> restore(CustomerStatus.ACTIVE).unblock(CLOCK))
          .isInstanceOf(DomainException.class);
    }
  }

  @Test
  @DisplayName("Restore keeps every stored value, including the version")
  void restoresStoredState() {
    CustomerId id = CustomerId.random();
    Customer customer =
        Customer.restore(
            id,
            USER_ID,
            "Baldev",
            "Parmar",
            ADULT_BIRTH_DATE,
            PHONE,
            CustomerStatus.BLOCKED,
            NOW,
            NOW.plusSeconds(5),
            7L);

    assertThat(customer.id()).isEqualTo(id);
    assertThat(customer.status()).isEqualTo(CustomerStatus.BLOCKED);
    assertThat(customer.updatedAt()).isEqualTo(NOW.plusSeconds(5));
    assertThat(customer.version()).isEqualTo(7L);
  }

  @Test
  @DisplayName("Equality is by id; toString contains no personal data")
  void identityAndToString() {
    Customer customer = register();
    Customer sameIdOtherState =
        Customer.restore(
            customer.id(),
            USER_ID,
            "Other",
            "Name",
            ADULT_BIRTH_DATE,
            PHONE,
            CustomerStatus.ACTIVE,
            NOW,
            NOW,
            3L);

    assertThat(customer).isEqualTo(sameIdOtherState).hasSameHashCodeAs(sameIdOtherState);
    assertThat(customer).isNotEqualTo(register());
    assertThat(customer.toString()).doesNotContain("Baldev", "Parmar", "+91", "2001");
  }

  private static Customer register() {
    return Customer.register(USER_ID, "Baldev", "Parmar", ADULT_BIRTH_DATE, PHONE, CLOCK);
  }

  private static Customer restore(CustomerStatus status) {
    return Customer.restore(
        CustomerId.random(),
        USER_ID,
        "Baldev",
        "Parmar",
        ADULT_BIRTH_DATE,
        PHONE,
        status,
        NOW,
        NOW,
        0L);
  }
}
