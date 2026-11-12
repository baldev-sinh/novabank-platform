package com.novabank.customer.infrastructure.persistence.customer.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.customer.application.exception.DuplicateCustomerException;
import com.novabank.customer.domain.model.Customer;
import com.novabank.customer.domain.model.CustomerStatus;
import com.novabank.customer.domain.valueobject.PhoneNumber;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import com.novabank.customer.support.TestcontainersConfiguration;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs the adapter against real PostgreSQL with the real Flyway schema. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaCustomerRepositoryAdapter.class})
class JpaCustomerRepositoryAdapterIT {

  private static final Clock CLOCK = Clock.systemUTC();

  @Autowired private JpaCustomerRepositoryAdapter adapter;

  @Test
  @DisplayName("Saves a customer and finds it again by id and user id")
  void savesAndFinds() {
    UserId userId = new UserId(UUID.randomUUID());

    Customer saved = adapter.save(customer(userId));

    assertThat(saved.version()).isZero();
    assertThat(adapter.existsByUserId(userId)).isTrue();
    assertThat(adapter.findByUserId(userId)).get().isEqualTo(saved);
    assertThat(adapter.findById(saved.id()))
        .get()
        .satisfies(found -> {
          assertThat(found.firstName()).isEqualTo("Baldev");
          assertThat(found.phone()).isEqualTo(PhoneNumber.of("+919876543210"));
          assertThat(found.dateOfBirth()).isEqualTo(LocalDate.of(2001, 1, 15));
          assertThat(found.status()).isEqualTo(CustomerStatus.PENDING_KYC);
        });
  }

  @Test
  @DisplayName("A second customer for the same user is rejected by the database")
  void oneCustomerPerUser() {
    UserId userId = new UserId(UUID.randomUUID());
    adapter.save(customer(userId));

    assertThatThrownBy(() -> adapter.save(customer(userId)))
        .isInstanceOf(DuplicateCustomerException.class);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("A stale update fails instead of overwriting a newer one")
  void staleUpdateFails() {
    Customer saved = adapter.save(customer(new UserId(UUID.randomUUID())));

    Customer first = adapter.findById(saved.id()).orElseThrow();
    Customer second = adapter.findById(saved.id()).orElseThrow();

    first.activate(CLOCK);
    assertThat(adapter.save(first).version()).isEqualTo(1L);

    second.block(CLOCK);
    assertThatThrownBy(() -> adapter.save(second))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  private static Customer customer(UserId userId) {
    return Customer.register(
        userId,
        "Baldev",
        "Parmar",
        LocalDate.of(2001, 1, 15),
        PhoneNumber.of("+919876543210"),
        CLOCK);
  }
}
