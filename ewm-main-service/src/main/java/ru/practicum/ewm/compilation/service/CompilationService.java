package ru.practicum.ewm.compilation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.common.OffsetPageRequest;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.repository.CompilationRepository;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.service.EventStatsService;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventStatsService statsService;

    @Transactional
    public CompilationDto createCompilation(NewCompilationDto request) {
        if (compilationRepository.existsByTitle(request.getTitle())) {
            throw new ConflictException(
                    "Compilation with title=" + request.getTitle() + " already exists");
        }

        Compilation compilation = new Compilation();
        compilation.setTitle(request.getTitle());
        compilation.setPinned(Boolean.TRUE.equals(request.getPinned()));
        compilation.setEvents(loadEvents(request.getEvents()));

        return toDto(compilationRepository.save(compilation));
    }

    @Transactional
    public void deleteCompilation(Long compId) {
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Compilation with id=" + compId + " was not found");
        }

        compilationRepository.deleteById(compId);
    }

    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest request) {
        Compilation compilation = getCompilationOrThrow(compId);

        if (request.getTitle() != null && !request.getTitle().equals(compilation.getTitle())) {
            if (compilationRepository.existsByTitle(request.getTitle())) {
                throw new ConflictException(
                        "Compilation with title=" + request.getTitle() + " already exists");
            }
            compilation.setTitle(request.getTitle());
        }

        if (request.getPinned() != null) {
            compilation.setPinned(request.getPinned());
        }

        if (request.getEvents() != null) {
            compilation.setEvents(loadEvents(request.getEvents()));
        }

        return toDto(compilationRepository.save(compilation));
    }

    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        OffsetPageRequest page = OffsetPageRequest.of(from, size, Sort.by("id"));

        List<Compilation> compilations = pinned == null
                ? compilationRepository.findAllBy(page)
                : compilationRepository.findAllByPinned(pinned, page);

        return toDtos(compilations);
    }

    public CompilationDto getCompilation(Long compId) {
        return toDto(getCompilationOrThrow(compId));
    }

    private Compilation getCompilationOrThrow(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException(
                        "Compilation with id=" + compId + " was not found"));
    }

    private Set<Event> loadEvents(Set<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return new HashSet<>();
        }

        List<Event> events = eventRepository.findAllById(eventIds);

        if (events.size() != eventIds.size()) {
            throw new NotFoundException("Some events of the compilation were not found");
        }

        return new HashSet<>(events);
    }

    private CompilationDto toDto(Compilation compilation) {
        return toDtos(List.of(compilation)).get(0);
    }

    private List<CompilationDto> toDtos(List<Compilation> compilations) {
        List<Event> allEvents = compilations.stream()
                .flatMap(c -> c.getEvents().stream())
                .distinct()
                .toList();

        List<Long> eventIds = allEvents.stream()
                .map(Event::getId)
                .toList();

        Map<Long, Long> confirmed = eventMapper.getConfirmedCounts(eventIds);
        Map<Long, Long> views = statsService.getViews(allEvents);

        return compilations.stream()
                .map(c -> new CompilationDto(
                        c.getId(),
                        c.getPinned(),
                        c.getTitle(),
                        c.getEvents().stream()
                                .sorted(Comparator.comparing(Event::getId))
                                .map(e -> eventMapper.toShortDto(
                                        e,
                                        confirmed.getOrDefault(e.getId(), 0L),
                                        views.getOrDefault(e.getId(), 0L)))
                                .toList()))
                .toList();
    }
}