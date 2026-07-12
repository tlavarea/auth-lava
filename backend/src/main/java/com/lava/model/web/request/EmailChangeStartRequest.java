package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RecordBuilder
public record EmailChangeStartRequest(@NotBlank @Email String newEmail) implements EmailChangeStartRequestBuilder.With {

    @Override
    public String toString() {
        return String.format("EmailChangeStartRequest{newEmail='%s'}", newEmail);
    }
}
