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
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventStatsService {

    private static final String APP = "ewm-main-service";
    private static final String EVENTS_PREFIX = "/events/";
    private static final int MAX_URIS_IN_REQUEST = 100;
    private static final LocalDateTime RANKING_START = LocalDateTime.of(2000, 1, 1, 0, 0);
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
        List<Event> published = events.stream()
                .filter(e -> e.getPublishedOn() != null)
                .toList();

        if (published.isEmpty()) {
            return new HashMap<>();
        }

        try {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = published.stream()
                    .map(Event::getPublishedOn)
                    .min(Comparator.naturalOrder())
                    .orElse(end.minusYears(1));

            List<String> uris = published.size() > MAX_URIS_IN_REQUEST
                    ? null
                    : published.stream().map(e -> EVENTS_PREFIX + e.getId()).toList();

            return toViewsMap(statsClient.getStats(start, end, uris, true));
        } catch (Exception e) {
            log.warn("Failed to get views from the stats service: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public Map<Long, Long> getViewsRanking() {
        try {
            Map<Long, Long> views = toViewsMap(
                    statsClient.getStats(RANKING_START, LocalDateTime.now(), null, true));

            return views.entrySet().stream()
                    .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()
                            .thenComparing(Map.Entry.<Long, Long>comparingByKey()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> a,
                            LinkedHashMap::new));
        } catch (Exception e) {
            log.warn("Failed to get views ranking from the stats service: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Map<Long, Long> toViewsMap(List<ViewStatsDto> stats) {
        Map<Long, Long> views = new HashMap<>();

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
                continue;
            }
        }

        return views;
    }
}