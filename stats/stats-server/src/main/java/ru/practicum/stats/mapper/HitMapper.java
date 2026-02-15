package ru.practicum.stats.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.model.Hit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@UtilityClass
public class HitMapper {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Hit toHit(EndpointHit dto) {
        return Hit.builder()
                .app(dto.getApp())
                .uri(dto.getUri())
                .ip(dto.getIp())
                .timestamp(LocalDateTime.parse(dto.getTimestamp(), FORMATTER))
                .build();
    }
}