package com.quan.userservice.user;

import com.quan.userservice.common.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class ProfileController {
    private final UserRepository users;
    private final JwtService tokens;

    public record Profile(UUID userId, String timezone) {}
    public record Update(@NotBlank @Size(max = 50) String timezone) {}
    public record Updated(UUID userId, String timezone, String accessToken) {}

    @GetMapping
    @Transactional(readOnly = true)
    public Profile profile(Authentication auth) {
        User user = activeUser(auth);
        return new Profile(user.getId(), user.getTimezone());
    }

    @PatchMapping
    @Transactional
    public Updated update(Authentication auth, @Valid @RequestBody Update request) {
        User user = activeUser(auth);
        user.setTimezone(AccountTimezone.validate(request.timezone()));
        // Changing a preference must not extend the lifetime of an access token.
        String access = tokens.generateAccessToken(user, (Instant) auth.getDetails());
        return new Updated(user.getId(), user.getTimezone(), access);
    }

    private User activeUser(Authentication auth) {
        return users.findByIdAndDeletedAtIsNull((UUID) auth.getPrincipal())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
