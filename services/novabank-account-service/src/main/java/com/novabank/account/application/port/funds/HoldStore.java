package com.novabank.account.application.port.funds;

import com.novabank.account.domain.model.Hold;
import java.util.Optional;
import java.util.UUID;

public interface HoldStore {

  void save(Hold hold);

  Optional<Hold> findByPaymentId(UUID paymentId);

  void updateStatus(Hold hold);
}
