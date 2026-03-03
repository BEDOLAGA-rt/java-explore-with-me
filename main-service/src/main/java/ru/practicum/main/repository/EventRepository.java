package ru.practicum.main.repository;

import ru.practicum.main.model.Event;
import ru.practicum.main.model.User;
import ru.practicum.main.model.enums.State;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Page<Event> findAllByInitiator(User initiator, Pageable pageable);

    Optional<Event> findByIdAndState(Long id, State state);

    @Query("SELECT e FROM Event e WHERE e.id IN :ids")
    List<Event> findAllByIds(List<Long> ids);

    boolean existsByCategoryId(Long categoryId);
}