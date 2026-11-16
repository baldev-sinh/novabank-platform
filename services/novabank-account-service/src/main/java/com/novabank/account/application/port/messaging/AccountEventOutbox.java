package com.novabank.account.application.port.messaging;

import com.novabank.account.application.event.AccountOpenedEvent;

/** Must be called inside the transaction that makes the change: both commit, or neither. */
public interface AccountEventOutbox {

  void add(AccountOpenedEvent event);
}
