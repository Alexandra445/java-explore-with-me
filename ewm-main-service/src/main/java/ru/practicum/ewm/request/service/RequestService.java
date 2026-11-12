package ru.practicum.ewm.request.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.request.dto.RequestDto;
import ru.practicum.ewm.request.model.ParticipationRequest;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestService {

    private final ParticipationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Transactional
    public RequestDto createRequest(Long userId, Long eventId) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "User with id=" + userId + " was not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Initiator cannot add a request to their own event");
        }

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot participate in an unpublished event");
        }

        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Request already exists");
        }

        if (event.getParticipantLimit() > 0) {
            long confirmed = requestRepository.countByEventIdAndStatus(
                    eventId, RequestStatus.CONFIRMED);
            if (confirmed >= event.getParticipantLimit()) {
                throw new ConflictException("The participant limit has been reached");
            }
        }

        ParticipationRequest request = new ParticipationRequest();
        request.setCreated(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        request.setEvent(event);
        request.setRequester(requester);

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            request.setStatus(RequestStatus.CONFIRMED);
        } else {
            request.setStatus(RequestStatus.PENDING);
        }

        return toDto(requestRepository.save(request));
    }

    public List<RequestDto> getUserRequests(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }

        return requestRepository.findAllByRequesterId(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public RequestDto cancelRequest(Long userId, Long requestId) {
        ParticipationRequest request = requestRepository.findById(requestId)
                .filter(r -> r.getRequester().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException(
                        "Request with id=" + requestId + " was not found"));

        request.setStatus(RequestStatus.CANCELED);

        return toDto(requestRepository.save(request));
    }

    public List<RequestDto> getEventRequests(Long userId, Long eventId) {
        eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        return requestRepository.findAllByEventId(eventId)
                .stream()
                .sorted(Comparator.comparing(ParticipationRequest::getId))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatus(
            Long userId, Long eventId, EventRequestStatusUpdateRequest update) {

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        if (update.getStatus() != RequestStatus.CONFIRMED
                && update.getStatus() != RequestStatus.REJECTED) {
            throw new BadRequestException("Status must be CONFIRMED or REJECTED");
        }

        List<RequestDto> confirmedList = new ArrayList<>();
        List<RequestDto> rejectedList = new ArrayList<>();

        int limit = event.getParticipantLimit() == null ? 0 : event.getParticipantLimit();
        boolean moderation = event.getRequestModeration() == null || event.getRequestModeration();

        if (limit == 0 || !moderation) {
            return new EventRequestStatusUpdateResult(confirmedList, rejectedList);
        }

        List<ParticipationRequest> requests = requestRepository
                .findAllByIdIn(update.getRequestIds())
                .stream()
                .sorted(Comparator.comparing(ParticipationRequest::getId))
                .toList();

        if (requests.size() != new HashSet<>(update.getRequestIds()).size()) {
            throw new NotFoundException("Some requests were not found");
        }

        for (ParticipationRequest request : requests) {
            if (!request.getEvent().getId().equals(eventId)) {
                throw new NotFoundException(
                        "Request with id=" + request.getId() + " was not found for this event");
            }
            if (request.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }
        }

        if (update.getStatus() == RequestStatus.REJECTED) {
            for (ParticipationRequest request : requests) {
                request.setStatus(RequestStatus.REJECTED);
                rejectedList.add(toDto(request));
            }
            return new EventRequestStatusUpdateResult(confirmedList, rejectedList);
        }

        long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);

        if (confirmed >= limit) {
            throw new ConflictException("The participant limit has been reached");
        }

        for (ParticipationRequest request : requests) {
            if (confirmed < limit) {
                request.setStatus(RequestStatus.CONFIRMED);
                confirmedList.add(toDto(request));
                confirmed++;
            } else {
                request.setStatus(RequestStatus.REJECTED);
                rejectedList.add(toDto(request));
            }
        }

        if (confirmed >= limit) {
            List<ParticipationRequest> pending = requestRepository
                    .findAllByEventIdAndStatus(eventId, RequestStatus.PENDING);
            for (ParticipationRequest request : pending) {
                request.setStatus(RequestStatus.REJECTED);
                rejectedList.add(toDto(request));
            }
        }

        return new EventRequestStatusUpdateResult(confirmedList, rejectedList);
    }

    private RequestDto toDto(ParticipationRequest request) {
        return new RequestDto(
                request.getId(),
                request.getCreated(),
                request.getEvent().getId(),
                request.getRequester().getId(),
                request.getStatus()
        );
    }
}