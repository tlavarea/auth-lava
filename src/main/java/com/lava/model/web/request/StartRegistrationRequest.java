package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RecordBuilder
public record StartRegistrationRequest(@NotBlank @Email String email) implements StartRegistrationRequestBuilder.With {}
