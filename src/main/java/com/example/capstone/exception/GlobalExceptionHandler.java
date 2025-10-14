package com.example.capstone.exception;

import com.example.capstone.user.exception.DuplicateNicknameException;
import com.example.capstone.user.exception.InvalidTokenException;
import com.example.capstone.user.exception.TokenExpiredException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.persistence.EntityNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({EntityNotFoundException.class, DuplicateNicknameException.class})
    public ResponseEntity<ErrorDetails> handleUserException(RuntimeException ex, WebRequest request) {
        ErrorDetails errorDetails = getErrorDetails(ex, request);
        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorDetails> handleIllegalArgumentException(RuntimeException ex, WebRequest request) {
        ErrorDetails errorDetails = getErrorDetails(ex, request);
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorDetails> handleTokenExpiredException(RuntimeException ex, WebRequest request) {
        ErrorDetails errorDetails = getErrorDetails(ex, request);
        return new ResponseEntity<>(errorDetails, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorDetails> handleRuntimeException(RuntimeException ex, WebRequest request) {
        ErrorDetails errorDetails = getErrorDetails(ex, request);
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @NotNull
    private static ErrorDetails getErrorDetails(RuntimeException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                ex.getMessage(),
                request.getDescription(false),
                null);
        return errorDetails;
    }

}
