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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicEventService {

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
            List<Event> all = eventRepository.findAll(spec, Sort.by("id"));
            Map<Long, Long> allViews = statsService.getViews(all);

            events = all.stream()
                    .sorted(Comparator
                            .comparing((Event e) -> allViews.getOrDefault(e.getId(), 0L),
                                    Comparator.reverseOrder())
                            .thenComparing(Event::getId))
                    .skip(from)
                    .limit(size)
                    .toList();
            views = allViews;
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
}