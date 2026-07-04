package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;

@RecordBuilder
public record RefreshRequest(@NotBlank String refreshToken) implements RefreshRequestBuilder.With {

    @Override
    public String toString() {
        return "RefreshRequest{refreshToken='[redacted]'}";
    }
}
