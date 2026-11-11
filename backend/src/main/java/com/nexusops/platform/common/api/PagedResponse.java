package com.nexusops.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Standard envelope for paginated collection API responses.
 *
 * <pre>
 * {
 *   "data": [ ... ],
 *   "page": 1,
 *   "pageSize": 20,
 *   "totalItems": 143,
 *   "totalPages": 8
 * }
 * </pre>
 *
 * @param <T> the element type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PagedResponse<T>(
        List<T> data,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {

    public static <T> PagedResponse<T> of(List<T> data, int page, int pageSize, long totalItems) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalItems / pageSize) : 1;
        return new PagedResponse<>(data, page, pageSize, totalItems, totalPages);
    }
}

