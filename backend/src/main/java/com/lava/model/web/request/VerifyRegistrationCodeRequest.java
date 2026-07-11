package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RecordBuilder
public record VerifyRegistrationCodeRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String code) implements VerifyRegistrationCodeRequestBuilder.With {}
