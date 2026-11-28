package com.quan.budgetservice.common.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.time.ZoneId;

public final class AccountZone {
    private static final ZoneId DEFAULT = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Set<String> REGION_IDS = ZoneId.getAvailableZoneIds();

    private AccountZone() {}

    public static ZoneId fromClaim(Object value) {
        if (value instanceof String id && id.contains("/") && REGION_IDS.contains(id)) {
            return ZoneId.of(id);
        }
        return DEFAULT;
    }

    public static ZoneId current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof ZoneId zone ? zone : DEFAULT;
    }
}
