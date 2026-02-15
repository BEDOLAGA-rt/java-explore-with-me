package ru.practicum.stats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EndpointHit {
    @NotBlank
    @Size(max = 255)
    private String app;

    @NotBlank
    @Size(max = 512)
    private String uri;

    @NotBlank
    @Size(max = 15)
    private String ip;

    @NotBlank
    private String timestamp; // формат "yyyy-MM-dd HH:mm:ss"
}