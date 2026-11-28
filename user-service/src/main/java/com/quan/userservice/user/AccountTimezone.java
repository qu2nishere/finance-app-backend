package com.quan.userservice.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.time.ZoneId;

public final class AccountTimezone {
    private static final Set<String> REGION_IDS = ZoneId.getAvailableZoneIds();

    private AccountTimezone() {}

    public static String validate(String value) {
        if (value == null || !value.contains("/") || !REGION_IDS.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Múi giờ không hợp lệ");
        }
        return value;
    }
}
