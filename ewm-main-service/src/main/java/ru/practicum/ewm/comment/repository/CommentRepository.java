package ru.practicum.ewm.comment.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.practicum.ewm.comment.model.Comment;

import java.util.List;

public interface CommentRepository
        extends JpaRepository<Comment, Long>, JpaSpecificationExecutor<Comment> {

    @EntityGraph(attributePaths = {"author"})
    List<Comment> findAllByEventId(Long eventId, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    List<Comment> findAllByAuthorId(Long authorId, Pageable pageable);
}