package com.novabank.customer.application.port.messaging;

import com.novabank.customer.application.event.CustomerVerifiedEvent;

/**
 * Records customer events for publishing. Must be called inside the transaction that makes the
 * change, so the change and its event are committed together or not at all.
 */
public interface CustomerEventOutbox {

  void add(CustomerVerifiedEvent event);
}
