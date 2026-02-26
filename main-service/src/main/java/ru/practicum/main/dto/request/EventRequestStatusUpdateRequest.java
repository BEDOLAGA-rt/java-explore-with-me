package ru.practicum.main.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class EventRequestStatusUpdateRequest {
    private List<Long> requestIds;
    private String status; // CONFIRMED, REJECTED
}