package com.myhealth.common;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        ErrorCode error,
        String message,
        String path,
        List<FieldErrorDetail> details
) {
    public record FieldErrorDetail(String field, String code, String message) {
    }
}
