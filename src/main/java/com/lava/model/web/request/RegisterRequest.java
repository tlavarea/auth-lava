package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RecordBuilder
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 32) String password) implements RegisterRequestBuilder.With {

    @Override
    public String toString() {
        return String.format("RegisterRequest{email='%s'}", email);
    }
}
