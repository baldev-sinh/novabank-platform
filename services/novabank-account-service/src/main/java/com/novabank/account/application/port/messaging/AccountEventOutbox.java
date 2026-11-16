package com.novabank.account.application.port.messaging;

import com.novabank.account.application.event.AccountEvent;

/** Must be called inside the transaction that makes the change: both commit, or neither. */
public interface AccountEventOutbox {

  void add(AccountEvent event);
}
