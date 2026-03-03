package ru.practicum.main.service;

import ru.practicum.main.dto.comment.CommentDto;
import ru.practicum.main.dto.comment.NewCommentDto;
import ru.practicum.main.dto.comment.UpdateCommentRequest;

import java.util.List;

public interface CommentService {
    CommentDto addComment(Long userId, Long eventId, NewCommentDto dto);
    CommentDto updateComment(Long userId, Long commentId, UpdateCommentRequest dto);
    void deleteCommentByUser(Long userId, Long commentId);
    void deleteCommentByAdmin(Long commentId);
    List<CommentDto> getEventComments(Long eventId, int from, int size);
    List<CommentDto> getUserComments(Long userId, int from, int size);
}