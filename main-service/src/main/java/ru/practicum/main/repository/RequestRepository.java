package ru.practicum.main.repository;

import ru.practicum.main.model.Event;
import ru.practicum.main.model.Request;
import ru.practicum.main.model.User;
import ru.practicum.main.model.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {
    List<Request> findAllByRequester(User requester);

    List<Request> findAllByEvent(Event event);

    Optional<Request> findByEventAndRequester(Event event, User requester);

    Integer countByEventAndStatus(Event event, RequestStatus status);

    List<Request> findAllByIdIn(List<Long> ids);

    @Modifying
    @Query("UPDATE Request r SET r.status = :status WHERE r.id IN :ids")
    void updateStatusByIds(List<Long> ids, RequestStatus status);
}