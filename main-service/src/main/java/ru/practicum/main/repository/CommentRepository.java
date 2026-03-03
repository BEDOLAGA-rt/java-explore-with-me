package ru.practicum.main.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.main.model.Comment;
import ru.practicum.main.model.Event;
import ru.practicum.main.model.User;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findAllByEvent(Event event, Pageable pageable);
    Page<Comment> findAllByAuthor(User author, Pageable pageable);
}