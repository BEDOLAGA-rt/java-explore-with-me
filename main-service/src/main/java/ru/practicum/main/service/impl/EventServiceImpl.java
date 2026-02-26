package ru.practicum.main.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.client.StatService;
import ru.practicum.main.dto.event.EventFullDto;
import ru.practicum.main.dto.event.EventShortDto;
import ru.practicum.main.dto.event.LocationDto;
import ru.practicum.main.dto.event.NewEventDto;
import ru.practicum.main.dto.event.UpdateEventAdminRequest;
import ru.practicum.main.dto.event.UpdateEventUserRequest;
import ru.practicum.main.exception.BadRequestException;
import ru.practicum.main.exception.ConflictException;
import ru.practicum.main.exception.NotFoundException;
import ru.practicum.main.mapper.EventMapper;
import ru.practicum.main.model.Category;
import ru.practicum.main.model.Event;
import ru.practicum.main.model.Location;
import ru.practicum.main.model.User;
import ru.practicum.main.model.enums.State;
import ru.practicum.main.model.enums.StateActionAdmin;
import ru.practicum.main.model.enums.StateActionUser;
import ru.practicum.main.repository.CategoryRepository;
import ru.practicum.main.repository.EventRepository;
import ru.practicum.main.repository.UserRepository;
import ru.practicum.main.service.EventService;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final StatService statService;

    // ==================== Private methods ====================

    @Override
    @Transactional
    public EventFullDto addEvent(Long userId, NewEventDto dto) {
        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));

        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException("Category with id=" + dto.getCategory() + " not found"));

        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + dto.getEventDate());
        }

        Event event = EventMapper.toEvent(dto, category, initiator);
        event = eventRepository.save(event);

        log.info("Added new event with id={} by user id={}", event.getId(), userId);
        return EventMapper.toEventFullDto(event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));

        Pageable pageable = PageRequest.of(from / size, size);
        return eventRepository.findAllByInitiator(initiator, pageable).stream()
                .map(EventMapper::toEventShortDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event with id=" + eventId + " not found for this user");
        }

        updateViews(List.of(event));
        return EventMapper.toEventFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest dto) {
        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event with id=" + eventId + " not found for this user");
        }

        if (event.getState() != State.PENDING && event.getState() != State.CANCELED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getCategory() != null) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category with id=" + dto.getCategory() + " not found"));
            event.setCategory(category);
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                throw new BadRequestException("Event date must be at least 2 hours later");
            }
            event.setEventDate(dto.getEventDate());
        }
        if (dto.getLocation() != null) {
            event.setLocation(toModelLocation(dto.getLocation()));
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }

        if (dto.getStateAction() != null) {
            String actionStr = dto.getStateAction();
            StateActionUser action;
            try {
                action = StateActionUser.valueOf(actionStr);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown state action: " + actionStr);
            }

            if (action == StateActionUser.SEND_TO_REVIEW) {
                event.setState(State.PENDING);
            } else if (action == StateActionUser.CANCEL_REVIEW) {
                event.setState(State.CANCELED);
            }
        }

        event = eventRepository.save(event);
        log.info("Updated event id={} by user id={}", eventId, userId);
        return EventMapper.toEventFullDto(event);
    }

    // ==================== Admin methods ====================

    @Override
    @Transactional(readOnly = true)
    public List<EventFullDto> getEventsByAdmin(List<Long> users, List<String> states, List<Long> categories,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               int from, int size) {
        // финальные переменные для использования в лямбде
        final List<Long> usersParam = users;
        final List<String> statesParam = states;
        final List<Long> categoriesParam = categories;
        final LocalDateTime startParam = rangeStart;
        final LocalDateTime endParam = rangeEnd;

        Specification<Event> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (usersParam != null && !usersParam.isEmpty()) {
                predicates.add(root.get("initiator").get("id").in(usersParam));
            }
            if (statesParam != null && !statesParam.isEmpty()) {
                List<State> stateEnums = statesParam.stream()
                        .map(State::valueOf)
                        .collect(Collectors.toList());
                predicates.add(root.get("state").in(stateEnums));
            }
            if (categoriesParam != null && !categoriesParam.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categoriesParam));
            }
            if (startParam != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventDate"), startParam));
            }
            if (endParam != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventDate"), endParam));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();
        updateViews(events);
        return events.stream()
                .map(EventMapper::toEventFullDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " not found"));

        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getCategory() != null) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category with id=" + dto.getCategory() + " not found"));
            event.setCategory(category);
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new BadRequestException("Event date must be at least 1 hour later");
            }
            event.setEventDate(dto.getEventDate());
        }
        if (dto.getLocation() != null) {
            event.setLocation(toModelLocation(dto.getLocation()));
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }

        if (dto.getStateAction() != null) {
            String actionStr = dto.getStateAction();
            StateActionAdmin action;
            try {
                action = StateActionAdmin.valueOf(actionStr);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown state action: " + actionStr);
            }

            if (action == StateActionAdmin.PUBLISH_EVENT) {
                if (event.getState() != State.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                }
                if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                    throw new ConflictException("Event date must be at least 1 hour later");
                }
                event.setState(State.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (action == StateActionAdmin.REJECT_EVENT) {
                if (event.getState() == State.PUBLISHED) {
                    throw new ConflictException("Cannot reject published event");
                }
                event.setState(State.CANCELED);
            }
        }

        event = eventRepository.save(event);
        log.info("Admin updated event id={}", eventId);
        return EventMapper.toEventFullDto(event);
    }

    // ==================== Public methods ====================

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, String sort,
                                               int from, int size, HttpServletRequest request) {
        statService.hit("ewm-main-service", request.getRequestURI(), request.getRemoteAddr(), LocalDateTime.now());

        // финальные переменные для использования в лямбде
        final LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        final LocalDateTime end = rangeEnd != null ? rangeEnd : LocalDateTime.now().plusYears(100);

        Specification<Event> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("eventDate"), start));
            predicates.add(cb.lessThanOrEqualTo(root.get("eventDate"), end));
            predicates.add(cb.equal(root.get("state"), State.PUBLISHED));

            if (text != null && !text.isBlank()) {
                String pattern = "%" + text.toLowerCase() + "%";
                Predicate annotationLike = cb.like(cb.lower(root.get("annotation")), pattern);
                Predicate descriptionLike = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(annotationLike, descriptionLike));
            }
            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categories));
            }
            if (paid != null) {
                predicates.add(cb.equal(root.get("paid"), paid));
            }
            if (onlyAvailable != null && onlyAvailable) {
                predicates.add(cb.or(
                        cb.equal(root.get("participantLimit"), 0),
                        cb.lessThan(root.get("confirmedRequests"), root.get("participantLimit"))
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        updateViews(events);

        if (sort != null) {
            if (sort.equals("EVENT_DATE")) {
                events.sort(Comparator.comparing(Event::getEventDate));
            } else if (sort.equals("VIEWS")) {
                events.sort(Comparator.comparing(Event::getViews).reversed());
            }
        }

        return events.stream()
                .map(EventMapper::toEventShortDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getPublicEventById(Long id, HttpServletRequest request) {
        Event event = eventRepository.findByIdAndState(id, State.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Event with id=" + id + " not found"));

        statService.hit("ewm-main-service", request.getRequestURI(), request.getRemoteAddr(), LocalDateTime.now());

        LocalDateTime start = event.getPublishedOn() != null ? event.getPublishedOn() : LocalDateTime.now().minusYears(1);
        List<String> uris = List.of("/events/" + id);
        var stats = statService.getStats(start, LocalDateTime.now(), uris, true);
        long views = stats.isEmpty() ? 0 : stats.get(0).getHits();
        event.setViews(views);

        return EventMapper.toEventFullDto(event);
    }

    // ==================== Вспомогательные методы ====================

    private void updateViews(List<Event> events) {
        if (events.isEmpty()) return;
        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        LocalDateTime start = events.stream()
                .map(Event::getPublishedOn)
                .filter(d -> d != null)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(10));

        var stats = statService.getStats(start, LocalDateTime.now(), uris, true);
        var viewsMap = stats.stream()
                .collect(Collectors.toMap(
                        ru.practicum.stats.dto.ViewStats::getUri,
                        ru.practicum.stats.dto.ViewStats::getHits
                ));

        for (Event event : events) {
            String uri = "/events/" + event.getId();
            event.setViews(viewsMap.getOrDefault(uri, 0L));
        }
    }

    private Location toModelLocation(LocationDto dto) {
        if (dto == null) return null;
        return Location.builder()
                .lat(dto.getLat())
                .lon(dto.getLon())
                .build();
    }
}