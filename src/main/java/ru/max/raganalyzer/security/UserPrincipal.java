package ru.max.raganalyzer.security;

import java.util.UUID;

public record UserPrincipal(UUID userId, String email) {}
