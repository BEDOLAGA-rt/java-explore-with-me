package ru.practicum.main.mapper;

import ru.practicum.main.dto.request.ParticipationRequestDto;
import ru.practicum.main.model.Request;

public class RequestMapper {

    public static ParticipationRequestDto toParticipationRequestDto(Request request) {
        ParticipationRequestDto dto = new ParticipationRequestDto();
        dto.setId(request.getId());
        dto.setCreated(request.getCreated().toString()); // формат ISO? можно оставить строкой
        dto.setEvent(request.getEvent().getId());
        dto.setRequester(request.getRequester().getId());
        dto.setStatus(request.getStatus().toString());
        return dto;
    }
}