package ru.practicum.ewm.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.stats.client.StatsClient;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventStatsService {

    private static final String APP = "ewm-main-service";
    private static final String EVENTS_PREFIX = "/events/";
    private static final int MAX_URIS_IN_REQUEST = 100;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StatsClient statsClient;

    public void saveHit(HttpServletRequest request) {
        try {
            EndpointHitDto hit = new EndpointHitDto();
            hit.setApp(APP);
            hit.setUri(request.getRequestURI());
            hit.setIp(request.getRemoteAddr());
            hit.setTimestamp(LocalDateTime.now().format(FORMATTER));
            statsClient.hit(hit);
        } catch (Exception e) {
            log.warn("Failed to save hit to the stats service: {}", e.getMessage());
        }
    }

    public Map<Long, Long> getViews(List<Event> events) {
        Map<Long, Long> views = new HashMap<>();

        if (events.isEmpty()) {
            return views;
        }

        try {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = events.stream()
                    .map(Event::getPublishedOn)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(end.minusYears(1));

            List<String> uris = events.size() > MAX_URIS_IN_REQUEST
                    ? null
                    : events.stream().map(e -> EVENTS_PREFIX + e.getId()).toList();

            List<ViewStatsDto> stats = statsClient.getStats(start, end, uris, true);

            for (ViewStatsDto stat : stats) {
                if (!APP.equals(stat.getApp())
                        || stat.getUri() == null
                        || !stat.getUri().startsWith(EVENTS_PREFIX)) {
                    continue;
                }
                try {
                    Long eventId = Long.parseLong(stat.getUri().substring(EVENTS_PREFIX.length()));
                    views.put(eventId, stat.getHits());
                } catch (NumberFormatException ignored) {
                    // не URI события, пропускаем
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get views from the stats service: {}", e.getMessage());
        }

        return views;
    }
}