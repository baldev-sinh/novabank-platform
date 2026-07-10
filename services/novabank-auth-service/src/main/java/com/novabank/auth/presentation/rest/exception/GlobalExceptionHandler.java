package com.novabank.auth.presentation.rest.exception;

import com.novabank.auth.application.exception.DuplicateEmailException;
import com.novabank.auth.domain.exception.DomainException;
import com.novabank.auth.presentation.rest.exception.response.ApiError;
import com.novabank.auth.presentation.rest.exception.response.ValidationApiError;
import com.novabank.auth.presentation.rest.exception.response.ValidationError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiError> handleDuplicateEmailException(
        DuplicateEmailException ex,
        HttpServletRequest request
    ) {

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                new ApiError(
                    Instant.now(),
                    HttpStatus.CONFLICT.value(),
                    HttpStatus.CONFLICT.getReasonPhrase(),
                    ex.getMessage(),
                    request.getRequestURI()
                )
            );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationApiError> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {

        List<ValidationError> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fieldError -> new ValidationError(
                fieldError.getField(),
                fieldError.getDefaultMessage()
            ))
            .toList();

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                new ValidationApiError(
                    Instant.now(),
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    errors,
                    request.getRequestURI()
                )
            );
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomainException(
        DomainException ex,
        HttpServletRequest request
    ) {

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                new ApiError(
                    Instant.now(),
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    ex.getMessage(),
                    request.getRequestURI()
                )
            );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(
        Exception ex,
        HttpServletRequest request
    ) {

        LOGGER.error(
            "Unhandled exception while processing request [{} {}]",
            request.getMethod(),
            request.getRequestURI(),
            ex
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                new ApiError(
                    Instant.now(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                    "An unexpected error occurred.",
                    request.getRequestURI()
                )
            );
    }
}
