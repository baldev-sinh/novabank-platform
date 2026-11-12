package com.novabank.auth.application.port.messaging;

import com.novabank.auth.application.event.IntegrationEvent;

/**
 * Records an event to be published. Must be called inside the transaction that makes the change, so
 * the change and its event are committed together or not at all.
 */
public interface EventOutbox {

  void add(IntegrationEvent event);
}
