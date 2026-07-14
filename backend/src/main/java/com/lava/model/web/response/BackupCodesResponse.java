package com.lava.model.web.response;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record BackupCodesResponse(List<String> backupCodes, UserResponse user)
        implements BackupCodesResponseBuilder.With {}
