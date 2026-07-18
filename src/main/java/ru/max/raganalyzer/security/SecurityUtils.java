package ru.max.raganalyzer.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UserPrincipal currentUser() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal up) return up;
        throw new IllegalStateException("Пользователь не аутентифицирован");
    }

    public static UUID currentUserId() {
        return currentUser().userId();
    }
}
