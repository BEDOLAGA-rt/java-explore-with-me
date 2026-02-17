package ru.practicum.stats.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.stats.dto.ViewStats;
import ru.practicum.stats.model.Hit;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий для работы с записями статистики.
 */
public interface HitRepository extends JpaRepository<Hit, Long> {

    /**
     * Получает статистику с учётом уникальности IP.
     *
     * @param start начало периода
     * @param end   конец периода
     * @param uris  список URI (может быть null)
     * @return список статистики
     */
    @Query("SELECT new ru.practicum.stats.dto.ViewStats(h.app, h.uri, COUNT(DISTINCT h.ip)) "
            + "FROM Hit h "
            + "WHERE h.timestamp BETWEEN :start AND :end "
            + "AND (COALESCE(:uris, NULL) IS NULL OR h.uri IN :uris) "
            + "GROUP BY h.app, h.uri "
            + "ORDER BY COUNT(DISTINCT h.ip) DESC")
    List<ViewStats> findUniqueStats(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    @Param("uris") List<String> uris);

    /**
     * Получает статистику без учёта уникальности IP.
     *
     * @param start начало периода
     * @param end   конец периода
     * @param uris  список URI (может быть null)
     * @return список статистики
     */
    @Query("SELECT new ru.practicum.stats.dto.ViewStats(h.app, h.uri, COUNT(h.ip)) "
            + "FROM Hit h "
            + "WHERE h.timestamp BETWEEN :start AND :end "
            + "AND (COALESCE(:uris, NULL) IS NULL OR h.uri IN :uris) "
            + "GROUP BY h.app, h.uri "
            + "ORDER BY COUNT(h.ip) DESC")
    List<ViewStats> findNonUniqueStats(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       @Param("uris") List<String> uris);
}