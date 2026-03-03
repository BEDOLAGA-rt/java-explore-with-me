package ru.practicum.main.exception;

/**
 * Исключение для ошибок 400 Bad Request.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}