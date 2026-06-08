package com.myhealth.system;

import java.time.Instant;
import java.util.List;

public final class SystemDtos {
    private SystemDtos() {
    }

    public record ComponentStatus(
            String key,
            String label,
            String status,
            String detail
    ) {
    }

    public record SystemStatusResponse(
            String status,
            Instant checkedAt,
            List<ComponentStatus> components
    ) {
    }
}
