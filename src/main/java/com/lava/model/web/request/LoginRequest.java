package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RecordBuilder
public record LoginRequest(
        @NotBlank @Email String email, @NotBlank String password) implements LoginRequestBuilder.With {

    @Override
    public String toString() {
        return String.format("LoginRequest{email='%s'}", email);
    }
}
