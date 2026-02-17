package ru.practicum.stats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс сервиса статистики.
 */
@SpringBootApplication
public class StatsServer {
    public static void main(String[] args) {
        SpringApplication.run(StatsServer.class, args);
    }
}