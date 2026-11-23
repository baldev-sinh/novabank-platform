package com.novabank.audit.presentation.rest;

import com.novabank.platform.web.PlatformExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 problem details from the platform handler; this service adds none of its own. */
@RestControllerAdvice
public class GlobalExceptionHandler extends PlatformExceptionHandler {}
