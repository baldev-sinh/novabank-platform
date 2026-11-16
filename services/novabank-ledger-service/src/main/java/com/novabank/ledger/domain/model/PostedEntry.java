package com.novabank.ledger.domain.model;

import com.novabank.platform.money.Money;
import java.util.UUID;

/**
 * A posting as recorded, with the account's balance right after it.
 *
 * @param sequence the account's entry number (1, 2, 3, ...): lets projections ignore stale or
 *     duplicate updates
 */
public record PostedEntry(
    UUID accountId, Direction direction, Money amount, Money balanceAfter, long sequence) {}
