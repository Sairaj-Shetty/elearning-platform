package com.lumina.common;

import java.util.List;

public record ApiResponse<T>(boolean success, T data, ApiError error) {
    public record ApiError(String code, String message, List<String> details) {}

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(String code, String message, List<String> details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details == null ? List.of() : details));
    }
}
