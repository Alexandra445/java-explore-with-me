package ru.practicum.ewm.event.repository;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.request.model.ParticipationRequest;
import ru.practicum.ewm.request.model.RequestStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<Event> adminFilter(
            List<Long> users,
            List<EventState> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {

        return (root, query, cb) -> {
            fetchAssociations(root, query);

            List<Predicate> predicates = new ArrayList<>();

            if (users != null && !users.isEmpty()) {
                predicates.add(root.get("initiator").get("id").in(users));
            }
            if (states != null && !states.isEmpty()) {
                predicates.add(root.get("state").in(states));
            }
            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categories));
            }
            if (rangeStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventDate"), rangeStart));
            }
            if (rangeEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Event> publicFilter(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            boolean onlyAvailable) {

        return (root, query, cb) -> {
            fetchAssociations(root, query);

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("state"), EventState.PUBLISHED));

            if (text != null && !text.isBlank()) {
                String escaped = text.toLowerCase()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("annotation")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }

            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categories));
            }

            if (paid != null) {
                predicates.add(cb.equal(root.get("paid"), paid));
            }

            if (rangeStart == null && rangeEnd == null) {
                predicates.add(cb.greaterThan(root.get("eventDate"), LocalDateTime.now()));
            } else {
                if (rangeStart != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("eventDate"), rangeStart));
                }
                if (rangeEnd != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
                }
            }

            if (onlyAvailable) {
                Subquery<Long> confirmed = query.subquery(Long.class);
                Root<ParticipationRequest> requestRoot = confirmed.from(ParticipationRequest.class);
                confirmed.select(cb.count(requestRoot))
                        .where(cb.equal(requestRoot.get("event"), root),
                                cb.equal(requestRoot.get("status"), RequestStatus.CONFIRMED));

                predicates.add(cb.or(
                        cb.equal(root.get("participantLimit"), 0),
                        cb.gt(root.<Integer>get("participantLimit"), confirmed)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Event> idIn(Collection<Long> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }

    private static void fetchAssociations(Root<Event> root, CriteriaQuery<?> query) {
        boolean countQuery = query != null
                && (Long.class.equals(query.getResultType())
                || long.class.equals(query.getResultType()));

        if (!countQuery) {
            root.fetch("category", JoinType.LEFT);
            root.fetch("initiator", JoinType.LEFT);
        }
    }
}