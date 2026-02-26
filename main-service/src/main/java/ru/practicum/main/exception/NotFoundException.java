package ru.practicum.main.exception;

/**
 * Исключение для ошибок 404 Not Found.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}