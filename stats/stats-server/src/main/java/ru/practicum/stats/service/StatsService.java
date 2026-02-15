package ru.practicum.stats.service;

import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для работы со статистикой посещений.
 */
public interface StatsService {

    /**
     * Сохраняет информацию о запросе.
     *
     * @param hitDto данные запроса
     */
    void saveHit(EndpointHit hitDto);

    /**
     * Получает статистику за период.
     *
     * @param start  начало периода
     * @param end    конец периода
     * @param uris   список URI (может быть null)
     * @param unique флаг уникальности IP
     * @return список объектов статистики
     */
    List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique);
}