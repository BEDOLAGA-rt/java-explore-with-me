package ru.practicum.main.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.main.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.main.dto.request.ParticipationRequestDto;
import ru.practicum.main.exception.BadRequestException;
import ru.practicum.main.exception.ConflictException;
import ru.practicum.main.exception.NotFoundException;
import ru.practicum.main.mapper.RequestMapper;
import ru.practicum.main.model.Event;
import ru.practicum.main.model.Request;
import ru.practicum.main.model.User;
import ru.practicum.main.model.enums.RequestStatus;
import ru.practicum.main.model.enums.State;
import ru.practicum.main.repository.EventRepository;
import ru.practicum.main.repository.RequestRepository;
import ru.practicum.main.repository.UserRepository;
import ru.practicum.main.service.RequestService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public ParticipationRequestDto addRequest(Long userId, Long eventId) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Initiator cannot request participation in own event");
        }

        if (event.getState() != State.PUBLISHED) {
            throw new ConflictException("Event must be published");
        }

        if (requestRepository.findByEventAndRequester(event, requester).isPresent()) {
            throw new ConflictException("Request already exists");
        }

        if (event.getParticipantLimit() > 0) {
            long confirmedRequests = requestRepository.countByEventAndStatus(event, RequestStatus.CONFIRMED);
            if (confirmedRequests >= event.getParticipantLimit()) {
                throw new ConflictException("Participant limit reached");
            }
        }

        // Усекаем до микросекунд, чтобы избежать расхождений в тестах
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        Request request = Request.builder()
                .created(now)
                .event(event)
                .requester(requester)
                .status(needAutoConfirm(event) ? RequestStatus.CONFIRMED : RequestStatus.PENDING)
                .build();

        request = requestRepository.save(request);

        if (request.getStatus() == RequestStatus.CONFIRMED) {
            event.setConfirmedRequests(event.getConfirmedRequests() + 1);
            eventRepository.save(event);
        }

        log.info("Request added: userId={}, eventId={}, requestId={}", userId, eventId, request.getId());
        return RequestMapper.toParticipationRequestDto(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));

        return requestRepository.findAllByRequester(user).stream()
                .map(RequestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " not found"));

        if (!request.getRequester().getId().equals(userId)) {
            throw new NotFoundException("Request not found for this user");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("Only pending requests can be canceled");
        }

        request.setStatus(RequestStatus.CANCELED);
        request = requestRepository.save(request);

        log.info("Request canceled: userId={}, requestId={}", userId, requestId);
        return RequestMapper.toParticipationRequestDto(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event not found for this user");
        }

        return requestRepository.findAllByEvent(event).stream()
                .map(RequestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest dto) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event not found for this user");
        }

        List<Request> requests = requestRepository.findAllByIdIn(dto.getRequestIds());
        if (requests.size() != dto.getRequestIds().size()) {
            throw new NotFoundException("Some requests not found");
        }

        for (Request req : requests) {
            if (!req.getEvent().getId().equals(eventId)) {
                throw new ConflictException("Request with id=" + req.getId() + " does not belong to this event");
            }
            if (req.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }
        }

        RequestStatus newStatus;
        try {
            newStatus = RequestStatus.valueOf(dto.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + dto.getStatus());
        }

        List<ParticipationRequestDto> confirmedRequests = new ArrayList<>();
        List<ParticipationRequestDto> rejectedRequests = new ArrayList<>();

        if (newStatus == RequestStatus.CONFIRMED) {
            long confirmedCount = requestRepository.countByEventAndStatus(event, RequestStatus.CONFIRMED);
            int limit = event.getParticipantLimit();

            if (limit > 0 && confirmedCount >= limit) {
                throw new ConflictException("The participant limit has been reached");
            }

            for (Request req : requests) {
                if (limit > 0 && confirmedCount < limit) {
                    req.setStatus(RequestStatus.CONFIRMED);
                    confirmedCount++;
                    confirmedRequests.add(RequestMapper.toParticipationRequestDto(req));
                } else {
                    req.setStatus(RequestStatus.REJECTED);
                    rejectedRequests.add(RequestMapper.toParticipationRequestDto(req));
                }
            }
            event.setConfirmedRequests(confirmedCount);
            eventRepository.save(event);
        } else if (newStatus == RequestStatus.REJECTED) {
            for (Request req : requests) {
                req.setStatus(RequestStatus.REJECTED);
                rejectedRequests.add(RequestMapper.toParticipationRequestDto(req));
            }
        } else {
            throw new BadRequestException("Invalid status: " + newStatus);
        }

        requestRepository.saveAll(requests);

        log.info("Request status updated for eventId={}, confirmed={}, rejected={}",
                eventId, confirmedRequests.size(), rejectedRequests.size());

        EventRequestStatusUpdateResult result = new EventRequestStatusUpdateResult();
        result.setConfirmedRequests(confirmedRequests);
        result.setRejectedRequests(rejectedRequests);
        return result;
    }

    private boolean needAutoConfirm(Event event) {
        return !event.getRequestModeration() || event.getParticipantLimit() == 0;
    }
}