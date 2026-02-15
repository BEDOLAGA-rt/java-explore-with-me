package ru.practicum.stats.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;
import ru.practicum.stats.mapper.HitMapper;
import ru.practicum.stats.repository.HitRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {
    private final HitRepository repository;

    @Override
    @Transactional
    public void saveHit(EndpointHit hitDto) {
        repository.save(HitMapper.toHit(hitDto));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        if (Boolean.TRUE.equals(unique)) {
            return repository.findUniqueStats(start, end, uris);
        } else {
            return repository.findNonUniqueStats(start, end, uris);
        }
    }
}