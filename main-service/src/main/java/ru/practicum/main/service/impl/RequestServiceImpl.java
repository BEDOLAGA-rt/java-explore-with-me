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
        // Проверяем существование пользователя
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));

        // Проверяем существование события
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        // Инициатор события не может подать заявку
        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Initiator cannot request participation in own event");
        }

        // Событие должно быть опубликовано
        if (event.getState() != State.PUBLISHED) {
            throw new ConflictException("Event must be published");
        }

        // Нельзя повторный запрос
        if (requestRepository.findByEventAndRequester(event, requester).isPresent()) {
            throw new ConflictException("Request already exists");
        }

        // Проверка лимита участников
        if (event.getParticipantLimit() > 0) {
            long confirmedRequests = requestRepository.countByEventAndStatus(event, RequestStatus.CONFIRMED);
            if (confirmedRequests >= event.getParticipantLimit()) {
                throw new ConflictException("Participant limit reached");
            }
        }

        // Создаём запрос
        Request request = Request.builder()
                .created(LocalDateTime.now())
                .event(event)
                .requester(requester)
                .status(needAutoConfirm(event) ? RequestStatus.CONFIRMED : RequestStatus.PENDING)
                .build();

        request = requestRepository.save(request);

        // Если статус сразу CONFIRMED, увеличиваем счётчик подтверждённых запросов
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

        // Только запросы в статусе PENDING можно отменить
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
        // Проверяем, что пользователь - инициатор события
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
        // Проверка события и прав доступа
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event not found for this user");
        }

        // Получаем запросы по списку id
        List<Request> requests = requestRepository.findAllByIdIn(dto.getRequestIds());
        if (requests.size() != dto.getRequestIds().size()) {
            throw new NotFoundException("Some requests not found");
        }

        // Проверяем, что все запросы относятся к данному событию и имеют статус PENDING
        for (Request req : requests) {
            if (!req.getEvent().getId().equals(eventId)) {
                throw new ConflictException("Request with id=" + req.getId() + " does not belong to this event");
            }
            if (req.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }
        }

        // Преобразуем строку из DTO в enum
        RequestStatus newStatus;
        try {
            newStatus = RequestStatus.valueOf(dto.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + dto.getStatus());
        }

        List<ParticipationRequestDto> confirmedRequests = new ArrayList<>();
        List<ParticipationRequestDto> rejectedRequests = new ArrayList<>();

        if (newStatus == RequestStatus.CONFIRMED) {
            // Проверка лимита
            long confirmedCount = requestRepository.countByEventAndStatus(event, RequestStatus.CONFIRMED);
            int limit = event.getParticipantLimit();

            // Если лимит 0, ограничений нет
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
            // Обновляем счётчик подтверждённых запросов у события
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

    /**
     * Определяет, нужно ли автоматически подтверждать заявку.
     */
    private boolean needAutoConfirm(Event event) {
        // Автоподтверждение, если пре-модерация отключена или лимит участников 0
        return !event.getRequestModeration() || event.getParticipantLimit() == 0;
    }
}