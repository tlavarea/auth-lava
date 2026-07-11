package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RecordBuilder
public record MfaDisableRequest(
        @NotBlank @Size(min = 6, max = 12) String code) implements MfaDisableRequestBuilder.With {

    @Override
    public String toString() {
        return "MfaDisableRequest{}";
    }
}
