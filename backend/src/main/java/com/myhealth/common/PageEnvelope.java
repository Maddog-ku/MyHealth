package com.myhealth.common;

import java.util.List;

public record PageEnvelope<T>(List<T> data, int page, int size, long total) {
    public static <T> PageEnvelope<T> unpaged(List<T> data) {
        return new PageEnvelope<>(data, 0, data.size(), data.size());
    }
}
