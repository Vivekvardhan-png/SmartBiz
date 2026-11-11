package com.nexusops.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard envelope for successful single-resource or action API responses.
 *
 * <p>Collection endpoints return {@link PagedResponse} instead.
 *
 * <pre>
 * {
 *   "data": { ... }
 * }
 * </pre>
 *
 * @param <T> the payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}

