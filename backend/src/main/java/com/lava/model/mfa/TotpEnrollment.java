package com.lava.model.mfa;

import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record TotpEnrollment(Long mfaMethodId, String secret, String otpAuthUri, String qrCodeDataUri)
        implements TotpEnrollmentBuilder.With {}
