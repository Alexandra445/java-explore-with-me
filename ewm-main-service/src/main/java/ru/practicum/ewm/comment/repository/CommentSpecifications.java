package ru.practicum.ewm.comment.repository;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.practicum.ewm.comment.model.Comment;

import java.util.ArrayList;
import java.util.List;

public final class CommentSpecifications {

    private CommentSpecifications() {
    }

    public static Specification<Comment> adminFilter(Long eventId, Long userId) {
        return (root, query, cb) -> {
            boolean countQuery = query != null
                    && (Long.class.equals(query.getResultType())
                    || long.class.equals(query.getResultType()));

            if (!countQuery) {
                root.fetch("author", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (eventId != null) {
                predicates.add(cb.equal(root.get("event").get("id"), eventId));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("author").get("id"), userId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}