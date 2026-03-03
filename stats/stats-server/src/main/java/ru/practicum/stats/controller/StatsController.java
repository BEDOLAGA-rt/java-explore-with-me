package ru.practicum.stats.controller; // или ru.practicum.stats.server.controller – уточните по вашему проекту

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;
import ru.practicum.stats.exception.BadRequestException;
import ru.practicum.stats.service.StatsService;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @PostMapping("/hit")
    @ResponseStatus(HttpStatus.CREATED)
    public void hit(@Valid @RequestBody EndpointHit endpointHit) {
        log.info("Received hit: {}", endpointHit);
        statsService.saveHit(endpointHit); // <-- изменено с hit() на saveHit()
    }

    @GetMapping("/stats")
    public List<ViewStats> getStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(required = false) List<String> uris,
            @RequestParam(defaultValue = "false") Boolean unique) {

        log.info("Get stats request: start={}, end={}, uris={}, unique={}", start, end, uris, unique);

        // Валидация диапазона дат
        if (start.isAfter(end)) {
            throw new BadRequestException("Start date must be before end date");
        }

        return statsService.getStats(start, end, uris, unique);
    }
}