package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RecordBuilder
public record PasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 32) String newPassword) implements PasswordChangeRequestBuilder.With {

    @Override
    public String toString() {
        return "PasswordChangeRequest{}";
    }
}
