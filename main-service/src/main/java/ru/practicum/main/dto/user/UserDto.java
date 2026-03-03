package ru.practicum.main.dto.user;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String email;
    private String name;
}