package com.novabank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.novabank.customer.application.event.CustomerVerifiedEvent;
import com.novabank.customer.application.exception.CustomerNotFoundException;
import com.novabank.customer.application.exception.CustomerStateConflictException;
import com.novabank.customer.application.port.compliance.KycDecisionLog;
import com.novabank.customer.application.port.messaging.CustomerEventOutbox;
import com.novabank.customer.domain.model.Customer;
import com.novabank.customer.domain.model.CustomerStatus;
import com.novabank.customer.domain.repository.CustomerRepository;
import com.novabank.customer.domain.valueobject.PhoneNumber;
import com.novabank.customer.domain.valueobject.identifier.CustomerId;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApproveKycServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final UUID OFFICER = UUID.randomUUID();

  @Mock private CustomerRepository customers;
  @Mock private KycDecisionLog decisions;
  @Mock private CustomerEventOutbox events;

  private ApproveKycService service;

  @BeforeEach
  void setUp() {
    service = new ApproveKycService(customers, decisions, events, CLOCK);
  }

  @Test
  @DisplayName("Approval activates the customer, logs the decision and publishes CustomerVerified")
  void approves() {
    Customer customer = customer(CustomerStatus.PENDING_KYC);
    UUID customerId = customer.id().value();
    when(customers.findById(customer.id())).thenReturn(Optional.of(customer));
    when(customers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.approve(customerId, OFFICER).status()).isEqualTo(CustomerStatus.ACTIVE);

    verify(decisions).recordApproval(customerId, OFFICER, NOW);
    ArgumentCaptor<CustomerVerifiedEvent> event =
        ArgumentCaptor.forClass(CustomerVerifiedEvent.class);
    verify(events).add(event.capture());
    assertThat(event.getValue().customerId()).isEqualTo(customerId);
    assertThat(event.getValue().userId()).isEqualTo(customer.userId().value());
    assertThat(event.getValue().verifiedBy()).isEqualTo(OFFICER);
    assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("A retried approval changes nothing and publishes no second event")
  void retryIsIdempotent() {
    Customer customer = customer(CustomerStatus.ACTIVE);
    when(customers.findById(customer.id())).thenReturn(Optional.of(customer));

    assertThat(service.approve(customer.id().value(), OFFICER).status())
        .isEqualTo(CustomerStatus.ACTIVE);

    verifyNoInteractions(decisions, events);
  }

  @Test
  @DisplayName("Blocked or inactive customers can't be approved (409)")
  void rejectsOtherStatuses() {
    Customer customer = customer(CustomerStatus.BLOCKED);
    when(customers.findById(customer.id())).thenReturn(Optional.of(customer));

    assertThatThrownBy(() -> service.approve(customer.id().value(), OFFICER))
        .isInstanceOf(CustomerStateConflictException.class);
    verifyNoInteractions(decisions, events);
  }

  @Test
  @DisplayName("An unknown customer is a 404")
  void unknownCustomer() {
    when(customers.findById(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.approve(UUID.randomUUID(), OFFICER))
        .isInstanceOf(CustomerNotFoundException.class);
  }

  private static Customer customer(CustomerStatus status) {
    return Customer.restore(
        CustomerId.random(),
        new UserId(UUID.randomUUID()),
        "Baldev",
        "Parmar",
        LocalDate.of(2001, 1, 15),
        PhoneNumber.of("+919876543210"),
        status,
        NOW.minusSeconds(3600),
        NOW.minusSeconds(3600),
        0L);
  }
}
