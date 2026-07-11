package com.lava.service;

import com.lava.boot.autoconfigure.app.MfaProperties;
import com.lava.logging.LogSanitizer;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TotpServiceImpl implements TotpService {

    private final CodeVerifier codeVerifier;
    private final MfaProperties mfaProperties;
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();

    public TotpServiceImpl(MfaProperties mfaProperties) {
        this.mfaProperties = mfaProperties;

        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        verifier.setAllowedTimePeriodDiscrepancy(mfaProperties.totpAllowedDiscrepancy());
        this.codeVerifier = verifier;
    }

    @Override
    public String buildOtpAuthUri(String accountEmail, String secret) {
        return this.buildQrData(accountEmail, secret).getUri();
    }

    @Override
    public String generateQrCodeDataUri(String accountEmail, String secret) {
        try {
            byte[] png = this.qrGenerator.generate(this.buildQrData(accountEmail, secret));
            return Utils.getDataUriForImage(png, this.qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            log.error("generateQrCodeDataUri::failed: {}", LogSanitizer.sanitize(ExceptionUtils.getMessage(e)), e);
            throw new IllegalStateException("Failed to generate MFA enrollment QR code", e);
        }
    }

    @Override
    public String generateSecret() {
        return this.secretGenerator.generate();
    }

    @Override
    public boolean verifyCode(String secret, String submittedCode) {
        return this.codeVerifier.isValidCode(secret, submittedCode);
    }

    /**
     * Builds the shared TOTP metadata used both for the manual otpauth:// URI and the QR code that encodes it.
     *
     * @param accountEmail - the user's email, shown as the account label in an authenticator app.
     * @param secret - the base32-encoded TOTP secret.
     * @return the {@link QrData} describing this enrollment.
     */
    private QrData buildQrData(String accountEmail, String secret) {
        return new QrData.Builder()
                .label(accountEmail)
                .secret(secret)
                .issuer(this.mfaProperties.totpIssuer())
                .build();
    }
}
