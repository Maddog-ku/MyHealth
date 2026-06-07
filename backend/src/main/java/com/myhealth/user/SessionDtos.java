package com.myhealth.user;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class SessionDtos {
    private SessionDtos() {
    }

    public record SessionResponse(
            Long id,
            String device,
            Instant createdAt,
            Instant lastActiveAt,
            Instant expiresAt,
            boolean current
    ) {
    }

    public record SessionListResponse(List<SessionResponse> sessions) {
    }

    public record RevokeOthersRequest(@NotBlank String refreshToken) {
    }
}
