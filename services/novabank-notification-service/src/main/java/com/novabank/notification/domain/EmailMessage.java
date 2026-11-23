package com.novabank.notification.domain;

/** A rendered email; plain text, so nothing in it can be script or a tracking pixel. */
public record EmailMessage(String subject, String text) {}
