package ru.practicum.ewm.common;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class OffsetPageRequest implements Pageable {

    private final long offset;
    private final int limit;
    private final Sort sort;

    private OffsetPageRequest(long offset, int limit, Sort sort) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        this.offset = offset;
        this.limit = limit;
        this.sort = sort;
    }

    public static OffsetPageRequest of(int from, int size) {
        return new OffsetPageRequest(from, size, Sort.unsorted());
    }

    public static OffsetPageRequest of(int from, int size, Sort sort) {
        return new OffsetPageRequest(from, size, sort);
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageRequest(offset + limit, limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious()
                ? new OffsetPageRequest(Math.max(0, offset - limit), limit, sort)
                : first();
    }

    @Override
    public Pageable first() {
        return new OffsetPageRequest(0, limit, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageRequest((long) pageNumber * limit, limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}