package com.lava.model.web.response;

import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record RegistrationTokenResponse(String registrationToken) implements RegistrationTokenResponseBuilder.With {}
