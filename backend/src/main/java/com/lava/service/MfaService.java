package com.lava.service;

import com.lava.model.mfa.TotpEnrollment;
import com.lava.security.AuthUserPrincipal;
import java.util.List;

public interface MfaService {

    List<String> confirmEnrollment(AuthUserPrincipal principal, Long mfaMethodId, String code);

    boolean isEnrolled(Long userId);

    TotpEnrollment startEnrollment(AuthUserPrincipal principal);

    void verifyCode(Long userId, String code);
}
