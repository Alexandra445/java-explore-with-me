package ru.practicum.ewm.event.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.dto.LocationDto;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.EventRequestCount;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.user.dto.UserShortDto;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final ParticipationRequestRepository requestRepository;

    public Map<Long, Long> getConfirmedCounts(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return requestRepository.countByEventIdsAndStatus(eventIds, RequestStatus.CONFIRMED)
                .stream()
                .collect(Collectors.toMap(EventRequestCount::getEventId, EventRequestCount::getCnt));
    }

    public EventShortDto toShortDto(Event event, long confirmedRequests, long views) {
        return new EventShortDto(
                event.getAnnotation(),
                toCategoryDto(event),
                confirmedRequests,
                event.getEventDate(),
                event.getId(),
                toUserShortDto(event),
                event.getPaid(),
                event.getTitle(),
                views
        );
    }

    public EventFullDto toFullDto(Event event, long confirmedRequests, long views) {
        LocationDto location = event.getLocation() == null
                ? null
                : new LocationDto(event.getLocation().getLat(), event.getLocation().getLon());

        return new EventFullDto(
                event.getAnnotation(),
                toCategoryDto(event),
                confirmedRequests,
                event.getCreatedOn(),
                event.getDescription(),
                event.getEventDate(),
                event.getId(),
                toUserShortDto(event),
                location,
                event.getPaid(),
                event.getParticipantLimit(),
                event.getPublishedOn(),
                event.getRequestModeration(),
                event.getState(),
                event.getTitle(),
                views
        );
    }

    private CategoryDto toCategoryDto(Event event) {
        return new CategoryDto(event.getCategory().getId(), event.getCategory().getName());
    }

    private UserShortDto toUserShortDto(Event event) {
        return new UserShortDto(event.getInitiator().getId(), event.getInitiator().getName());
    }
}