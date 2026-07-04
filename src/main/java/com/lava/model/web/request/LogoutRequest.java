package com.lava.model.web.request;

import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record LogoutRequest(boolean allDevices) implements LogoutRequestBuilder.With {}
