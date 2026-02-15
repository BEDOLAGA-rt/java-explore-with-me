package ru.practicum.stats.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class StatsClient {
    private final RestTemplate rest;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public StatsClient(@Value("${stats-server.url}") String serverUrl, RestTemplateBuilder builder) {
        this.rest = builder
                .uriTemplateHandler(new DefaultUriBuilderFactory(serverUrl))
                .build();
    }

    public void hit(EndpointHit hitDto) {
        HttpEntity<EndpointHit> request = new HttpEntity<>(hitDto, defaultHeaders());
        rest.postForEntity("/hit", request, Void.class);
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end,
                                    List<String> uris, Boolean unique) {
        StringBuilder urlBuilder = new StringBuilder("/stats?start={start}&end={end}");
        if (uris != null && !uris.isEmpty()) {
            urlBuilder.append("&uris={uris}");
        }
        urlBuilder.append("&unique={unique}");

        String urisParam = uris != null ? String.join(",", uris) : "";

        Map<String, Object> params = Map.of(
                "start", start.format(FORMATTER),
                "end", end.format(FORMATTER),
                "uris", urisParam,
                "unique", unique != null ? unique : false
        );

        ResponseEntity<List<ViewStats>> response = rest.exchange(
                urlBuilder.toString(),
                HttpMethod.GET,
                new HttpEntity<>(defaultHeaders()),
                new ParameterizedTypeReference<List<ViewStats>>() {},
                params
        );
        return response.getBody();
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}