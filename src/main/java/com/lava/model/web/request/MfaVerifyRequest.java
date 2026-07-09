package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RecordBuilder
public record MfaVerifyRequest(
        @NotBlank @Size(min = 6, max = 12) String code) implements MfaVerifyRequestBuilder.With {

    @Override
    public String toString() {
        return "MfaVerifyRequest{}";
    }
}
