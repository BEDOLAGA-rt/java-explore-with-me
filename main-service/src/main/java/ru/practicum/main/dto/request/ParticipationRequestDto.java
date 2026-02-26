package ru.practicum.main.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

@Data
public class ParticipationRequestDto {
    private Long id;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private String created;
    private Long event;
    private Long requester;
    private String status; // PENDING, CONFIRMED, REJECTED, CANCELED
}