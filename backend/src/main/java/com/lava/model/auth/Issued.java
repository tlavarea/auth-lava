package com.lava.model.auth;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.LocalDateTime;

@RecordBuilder
public record Issued(String rawToken, Long id, LocalDateTime expiresAt, Long userId) implements IssuedBuilder.With {}
