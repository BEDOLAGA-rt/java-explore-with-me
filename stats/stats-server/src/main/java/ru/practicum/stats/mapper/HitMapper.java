package ru.practicum.stats.mapper;

import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.model.Hit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Утилитарный класс для преобразования DTO в сущность.
 */
public class HitMapper {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static Hit toHit(EndpointHit dto) {
        return Hit.builder()
                .app(dto.getApp())
                .uri(dto.getUri())
                .ip(dto.getIp())
                .timestamp(LocalDateTime.parse(dto.getTimestamp(), FORMATTER))
                .build();
    }
}