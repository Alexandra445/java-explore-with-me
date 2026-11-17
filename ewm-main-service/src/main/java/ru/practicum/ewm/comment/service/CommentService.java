package ru.practicum.ewm.comment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.repository.CommentRepository;
import ru.practicum.ewm.comment.repository.CommentSpecifications;
import ru.practicum.ewm.common.OffsetPageRequest;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.dto.UserShortDto;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Transactional
    public CommentDto createComment(Long userId, Long eventId, NewCommentDto request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "User with id=" + userId + " was not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Event with id=" + eventId + " was not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot comment on an unpublished event");
        }

        Comment comment = new Comment();
        comment.setText(request.getText());
        comment.setEvent(event);
        comment.setAuthor(author);
        comment.setCreated(now());

        return toDto(commentRepository.save(comment));
    }

    @Transactional
    public CommentDto updateComment(Long userId, Long commentId, NewCommentDto request) {
        checkUserExists(userId);

        Comment comment = getCommentOrThrow(commentId);
        checkAuthor(comment, userId);

        comment.setText(request.getText());
        comment.setUpdated(now());

        return toDto(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        checkUserExists(userId);

        Comment comment = getCommentOrThrow(commentId);
        checkAuthor(comment, userId);

        commentRepository.delete(comment);
    }

    public List<CommentDto> getUserComments(Long userId, int from, int size) {
        checkUserExists(userId);

        return commentRepository.findAllByAuthorId(userId, newestFirst(from, size))
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<CommentDto> getEventComments(Long eventId, int from, int size) {
        if (!eventRepository.existsByIdAndState(eventId, EventState.PUBLISHED)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        return commentRepository.findAllByEventId(eventId, newestFirst(from, size))
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<CommentDto> getAdminComments(Long eventId, Long userId, int from, int size) {
        return commentRepository.findAll(
                        CommentSpecifications.adminFilter(eventId, userId),
                        newestFirst(from, size))
                .getContent()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }

        commentRepository.deleteById(commentId);
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }
    }

    private Comment getCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException(
                        "Comment with id=" + commentId + " was not found"));
    }

    private void checkAuthor(Comment comment, Long userId) {
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ConflictException("Only the author can change or delete the comment");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    private OffsetPageRequest newestFirst(int from, int size) {
        return OffsetPageRequest.of(from, size, Sort.by(Sort.Direction.DESC, "created", "id"));
    }

    private CommentDto toDto(Comment comment) {
        return new CommentDto(
                comment.getId(),
                comment.getText(),
                comment.getEvent().getId(),
                new UserShortDto(comment.getAuthor().getId(), comment.getAuthor().getName()),
                comment.getCreated(),
                comment.getUpdated()
        );
    }
}