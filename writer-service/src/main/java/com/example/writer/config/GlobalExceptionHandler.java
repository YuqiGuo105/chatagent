package com.example.writer.config;

import com.example.writer.dto.ErrorResponse;
import com.example.writer.exception.OptimisticLockConflictException;
import com.example.writer.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (a, b) -> a));
        return ErrorResponse.builder()
                .status(400)
                .error("Validation Failed")
                .fieldErrors(fieldErrors)
                .timestamp(Instant.now())
                .build();
    }

    @ExceptionHandler(OptimisticLockConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(OptimisticLockConflictException ex) {
        return ErrorResponse.builder()
                .status(409)
                .error("Version Conflict")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return ErrorResponse.builder()
                .status(404)
                .error("Not Found")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }
}
