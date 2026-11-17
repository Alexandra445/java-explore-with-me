package ru.practicum.ewm.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.common.OffsetPageRequest;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventSort;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.EventSpecifications;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicEventService {

    private static final int CHUNK_SIZE = 200;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventStatsService statsService;

    public List<EventShortDto> getEvents(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            boolean onlyAvailable,
            EventSort sort,
            int from,
            int size,
            HttpServletRequest request) {

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("rangeStart must not be after rangeEnd");
        }

        Specification<Event> spec = EventSpecifications.publicFilter(
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable);

        List<Event> events;
        Map<Long, Long> views;

        if (sort == EventSort.VIEWS) {
            Map<Long, Long> ranking = statsService.getViewsRanking();
            events = findOrderedByViews(spec, ranking, from, size);
            views = ranking;
        } else {
            events = eventRepository.findAll(
                            spec,
                            OffsetPageRequest.of(from, size, Sort.by("eventDate", "id")))
                    .getContent();
            views = statsService.getViews(events);
        }

        Map<Long, Long> confirmed = eventMapper.getConfirmedCounts(
                events.stream().map(Event::getId).toList());

        statsService.saveHit(request);

        return events.stream()
                .map(e -> eventMapper.toShortDto(
                        e,
                        confirmed.getOrDefault(e.getId(), 0L),
                        views.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    public EventFullDto getEvent(Long eventId, HttpServletRequest request) {
        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        statsService.saveHit(request);

        long views = statsService.getViews(List.of(event)).getOrDefault(eventId, 0L);
        long confirmed = eventMapper.getConfirmedCounts(List.of(eventId))
                .getOrDefault(eventId, 0L);

        return eventMapper.toFullDto(event, confirmed, views);
    }


    private List<Event> findOrderedByViews(
            Specification<Event> spec, Map<Long, Long> ranking, int from, int size) {

        int needed = from + size;
        List<Event> ordered = new ArrayList<>();
        List<Long> rankedIds = new ArrayList<>(ranking.keySet());

        for (int i = 0; i < rankedIds.size() && ordered.size() < needed; i += CHUNK_SIZE) {
            List<Long> chunk = rankedIds.subList(i, Math.min(i + CHUNK_SIZE, rankedIds.size()));

            Map<Long, Event> found = eventRepository
                    .findAll(spec.and(EventSpecifications.idIn(chunk)))
                    .stream()
                    .collect(Collectors.toMap(Event::getId, Function.identity()));

            for (Long id : chunk) {
                Event event = found.get(id);
                if (event != null) {
                    ordered.add(event);
                }
            }
        }

        if (ordered.size() < needed) {
            int pageNumber = 0;
            boolean hasNext = true;

            while (ordered.size() < needed && hasNext) {
                Page<Event> page = eventRepository.findAll(
                        spec,
                        OffsetPageRequest.of(pageNumber * CHUNK_SIZE, CHUNK_SIZE, Sort.by("id")));

                for (Event event : page.getContent()) {
                    if (!ranking.containsKey(event.getId())) {
                        ordered.add(event);
                    }
                }

                hasNext = page.hasNext();
                pageNumber++;
            }
        }

        return ordered.stream()
                .skip(from)
                .limit(size)
                .toList();
    }
}