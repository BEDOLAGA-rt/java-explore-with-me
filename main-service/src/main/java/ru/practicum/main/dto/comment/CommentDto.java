package ru.practicum.main.dto.comment;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import ru.practicum.main.dto.user.UserShortDto;

@Data
public class CommentDto {
    private Long id;
    private String text;
    private Long eventId;
    private UserShortDto author;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private String created;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private String updated;
}