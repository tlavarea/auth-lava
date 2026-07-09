package com.lava.model.web.response;

import com.lava.model.mfa.TotpEnrollment;
import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record TotpEnrollmentResponse(Long mfaMethodId, String secret, String otpAuthUri, String qrCodeDataUri)
        implements TotpEnrollmentResponseBuilder.With {

    public static TotpEnrollmentResponse from(TotpEnrollment enrollment) {
        return TotpEnrollmentResponseBuilder.builder()
                .mfaMethodId(enrollment.mfaMethodId())
                .otpAuthUri(enrollment.otpAuthUri())
                .qrCodeDataUri(enrollment.qrCodeDataUri())
                .secret(enrollment.secret())
                .build();
    }
}
