package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@RecordBuilder
public record EnrollTotpVerifyRequest(
        @NotNull Long mfaMethodId,
        @NotBlank @Pattern(regexp = "\\d{6}") String code) implements EnrollTotpVerifyRequestBuilder.With {

    @Override
    public String toString() {
        return String.format("EnrollTotpVerifyRequest{mfaMethodId=%s}", mfaMethodId);
    }
}
