package com.lava.model.database.view;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.Set;

@RecordBuilder
public record AuthUserView(
        Long id,
        String email,
        String passwordHash,
        String status,
        boolean emailVerified,
        Set<String> roles,
        Set<String> permissions)
        implements AuthUserViewBuilder.With {}
