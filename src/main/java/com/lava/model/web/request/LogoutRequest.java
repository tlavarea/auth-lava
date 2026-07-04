package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record LogoutRequest(String refreshToken) implements LogoutRequestBuilder.With {

    @Override
    public String toString() {
        return "LogoutRequest{refreshToken='[redacted]'}";
    }
}
