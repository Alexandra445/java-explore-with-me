package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.common.OffsetPageRequest;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.dto.LocationDto;
import ru.practicum.ewm.event.dto.NewEventDto;
import ru.practicum.ewm.event.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.event.dto.UpdateEventUserRequest;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.EventSpecifications;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.EventRequestCount;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.user.dto.UserShortDto;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ParticipationRequestRepository requestRepository;
    private final EventStatsService statsService;

    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto request) {
        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "User with id=" + userId + " was not found"));

        Category category = categoryRepository.findById(request.getCategory())
                .orElseThrow(() -> new NotFoundException(
                        "Category with id=" + request.getCategory() + " was not found"));

        if (request.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConflictException(
                    "Field: eventDate. Error: must be at least 2 hours from now. Value: "
                            + request.getEventDate());
        }

        Event event = new Event();
        event.setTitle(request.getTitle());
        event.setAnnotation(request.getAnnotation());
        event.setDescription(request.getDescription());
        event.setCategory(category);
        event.setInitiator(initiator);
        event.setEventDate(request.getEventDate());
        event.setLocation(toLocation(request.getLocation()));
        event.setPaid(request.getPaid() != null && request.getPaid());
        event.setParticipantLimit(request.getParticipantLimit() != null
                ? request.getParticipantLimit() : 0);
        event.setRequestModeration(request.getRequestModeration() == null
                || request.getRequestModeration());
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());

        return toFullDto(eventRepository.save(event), 0L, 0L);
    }

    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }

        List<Event> events = eventRepository.findAllByInitiatorId(
                userId, OffsetPageRequest.of(from, size, Sort.by("id")));

        Map<Long, Long> confirmed = getConfirmedCounts(
                events.stream().map(Event::getId).toList());

        Map<Long, Long> views = statsService.getViews(events);

        return events.stream()
                .map(e -> toShortDto(
                        e,
                        confirmed.getOrDefault(e.getId(), 0L),
                        views.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    public EventFullDto getUserEvent(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        return toFullDto(event, getConfirmedCount(eventId), getViews(event));
    }

    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest request) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        if (event.getState() != EventState.PENDING && event.getState() != EventState.CANCELED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        LocalDateTime resultDate = request.getEventDate() != null
                ? request.getEventDate()
                : event.getEventDate();

        if (resultDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConflictException(
                    "Field: eventDate. Error: must be at least 2 hours from now. Value: "
                            + resultDate);
        }

        applyFields(event, request.getTitle(), request.getAnnotation(),
                request.getDescription(), request.getCategory(), request.getEventDate(),
                request.getLocation(), request.getPaid(), request.getParticipantLimit(),
                request.getRequestModeration());

        if (request.getStateAction() != null) {
            switch (request.getStateAction()) {
                case "SEND_TO_REVIEW" -> event.setState(EventState.PENDING);
                case "CANCEL_REVIEW" -> event.setState(EventState.CANCELED);
                default -> throw new BadRequestException(
                        "Unknown state action: " + request.getStateAction());
            }
        }

        Event saved = eventRepository.save(event);

        return toFullDto(saved, getConfirmedCount(eventId), getViews(saved));
    }

    public List<EventFullDto> getAdminEvents(
            List<Long> users,
            List<EventState> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            int from,
            int size) {

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("rangeStart must not be after rangeEnd");
        }

        List<Event> events = eventRepository.findAll(
                        EventSpecifications.adminFilter(users, states, categories, rangeStart, rangeEnd),
                        OffsetPageRequest.of(from, size, Sort.by("id")))
                .getContent();

        Map<Long, Long> confirmed = getConfirmedCounts(
                events.stream().map(Event::getId).toList());

        Map<Long, Long> views = statsService.getViews(events);

        return events.stream()
                .map(e -> toFullDto(
                        e,
                        confirmed.getOrDefault(e.getId(), 0L),
                        views.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    @Transactional
    public EventFullDto updateAdminEvent(Long eventId, UpdateEventAdminRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        if (request.getEventDate() != null
                && request.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ConflictException(
                    "Field: eventDate. Error: must be at least 1 hour from now. Value: "
                            + request.getEventDate());
        }

        applyFields(event, request.getTitle(), request.getAnnotation(),
                request.getDescription(), request.getCategory(), request.getEventDate(),
                request.getLocation(), request.getPaid(), request.getParticipantLimit(),
                request.getRequestModeration());

        if (request.getStateAction() != null) {
            switch (request.getStateAction()) {
                case "PUBLISH_EVENT" -> {
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException(
                                "Cannot publish the event because it's not in the right state: "
                                        + event.getState());
                    }
                    if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                        throw new ConflictException(
                                "Cannot publish the event because it starts in less than 1 hour");
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                }
                case "REJECT_EVENT" -> {
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException(
                                "Cannot reject the event because it's not in the right state: "
                                        + event.getState());
                    }
                    event.setState(EventState.CANCELED);
                }
                default -> throw new BadRequestException(
                        "Unknown state action: " + request.getStateAction());
            }
        }

        Event saved = eventRepository.save(event);

        return toFullDto(saved, getConfirmedCount(eventId), getViews(saved));
    }

    private void applyFields(Event event, String title, String annotation, String description,
                             Long categoryId, LocalDateTime eventDate, LocationDto location,
                             Boolean paid, Integer participantLimit, Boolean requestModeration) {
        if (title != null) {
            event.setTitle(title);
        }
        if (annotation != null) {
            event.setAnnotation(annotation);
        }
        if (description != null) {
            event.setDescription(description);
        }
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException(
                            "Category with id=" + categoryId + " was not found"));
            event.setCategory(category);
        }
        if (eventDate != null) {
            event.setEventDate(eventDate);
        }
        if (location != null) {
            event.setLocation(toLocation(location));
        }
        if (paid != null) {
            event.setPaid(paid);
        }
        if (participantLimit != null) {
            event.setParticipantLimit(participantLimit);
        }
        if (requestModeration != null) {
            event.setRequestModeration(requestModeration);
        }
    }

    private long getViews(Event event) {
        return statsService.getViews(List.of(event)).getOrDefault(event.getId(), 0L);
    }

    private long getConfirmedCount(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }

    private Map<Long, Long> getConfirmedCounts(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return requestRepository.countByEventIdsAndStatus(eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.toMap(EventRequestCount::getEventId, EventRequestCount::getCnt));
    }

    private EventFullDto toFullDto(Event event, Long confirmedRequests, Long views) {
        return new EventFullDto(
                event.getAnnotation(),
                toCategoryDto(event.getCategory()),
                confirmedRequests,
                event.getCreatedOn(),
                event.getDescription(),
                event.getEventDate(),
                event.getId(),
                toUserShortDto(event.getInitiator()),
                toLocationDto(event.getLocation()),
                event.getPaid(),
                event.getParticipantLimit(),
                event.getPublishedOn(),
                event.getRequestModeration(),
                event.getState(),
                event.getTitle(),
                views
        );
    }

    private EventShortDto toShortDto(Event event, Long confirmedRequests, Long views) {
        return new EventShortDto(
                event.getAnnotation(),
                toCategoryDto(event.getCategory()),
                confirmedRequests,
                event.getEventDate(),
                event.getId(),
                toUserShortDto(event.getInitiator()),
                event.getPaid(),
                event.getTitle(),
                views
        );
    }

    private CategoryDto toCategoryDto(Category category) {
        return new CategoryDto(category.getId(), category.getName());
    }

    private UserShortDto toUserShortDto(User user) {
        return new UserShortDto(user.getId(), user.getName());
    }

    private LocationDto toLocationDto(Location location) {
        if (location == null) {
            return null;
        }
        return new LocationDto(location.getLat(), location.getLon());
    }

    private Location toLocation(LocationDto dto) {
        Location location = new Location();
        location.setLat(dto.getLat());
        location.setLon(dto.getLon());
        return location;
    }
}