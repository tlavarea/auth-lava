package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RecordBuilder
public record CompleteRegistrationRequest(
        @NotBlank String registrationToken,
        @NotBlank @Size(min = 8, max = 32) String password) implements CompleteRegistrationRequestBuilder.With {

    @Override
    public String toString() {
        return "CompleteRegistrationRequest{}";
    }
}
