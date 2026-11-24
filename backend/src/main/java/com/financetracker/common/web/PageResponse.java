package com.financetracker.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** The API's page shape, so responses don't expose Spring Data's Page JSON. {@code page} is 0-based. */
public record PageResponse<T> (
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

}
