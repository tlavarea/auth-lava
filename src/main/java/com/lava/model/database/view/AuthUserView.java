package com.lava.model.database.view;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record AuthUserView(
        Long id,
        String email,
        String passwordHash,
        String status,
        boolean emailVerified,
        List<String> roles,
        List<String> permissions)
        implements AuthUserViewBuilder.With {}
