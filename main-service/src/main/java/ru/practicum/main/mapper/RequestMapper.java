package ru.practicum.main.mapper;

import ru.practicum.main.dto.request.ParticipationRequestDto;
import ru.practicum.main.model.Request;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class RequestMapper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    public static ParticipationRequestDto toParticipationRequestDto(Request request) {
        ParticipationRequestDto dto = new ParticipationRequestDto();
        dto.setId(request.getId());
        dto.setCreated(request.getCreated().truncatedTo(ChronoUnit.MICROS).format(FORMATTER));
        dto.setEvent(request.getEvent().getId());
        dto.setRequester(request.getRequester().getId());
        dto.setStatus(request.getStatus().toString());
        return dto;
    }
}