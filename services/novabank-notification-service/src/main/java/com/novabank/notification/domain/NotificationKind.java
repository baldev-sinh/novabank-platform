package com.novabank.notification.domain;

public enum NotificationKind {
  /** To the sender: a transfer completed. */
  TRANSFER_SENT,
  /** To the sender: a transfer failed and nothing left the account. */
  TRANSFER_FAILED,
  /** To the owner of the credited account: a transfer or deposit arrived. */
  MONEY_RECEIVED,
  /** To the owner: a new account is ready to use. */
  ACCOUNT_READY
}
