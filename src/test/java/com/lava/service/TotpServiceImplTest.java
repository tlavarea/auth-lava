package com.lava.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.boot.autoconfigure.app.MfaProperties;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import org.junit.jupiter.api.Test;

class TotpServiceImplTest {

    private final TotpServiceImpl service = new TotpServiceImpl(properties());

    @Test
    void generateSecret_returnsNonBlankBase32String() {
        String secret = this.service.generateSecret();

        assertThat(secret).isNotBlank();
        assertThat(secret).matches("^[A-Z2-7]+=*$");
    }

    @Test
    void generateSecret_successiveCalls_produceDifferentSecrets() {
        assertThat(this.service.generateSecret()).isNotEqualTo(this.service.generateSecret());
    }

    @Test
    void buildOtpAuthUri_containsIssuerLabelAndSecret() {
        String uri = this.service.buildOtpAuthUri("user@example.com", "SECRET123");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=SECRET123");
        assertThat(uri).contains("issuer=auth-lava-test");
        assertThat(uri).contains("user%40example.com");
    }

    @Test
    void generateQrCodeDataUri_returnsPngDataUri() {
        String dataUri = this.service.generateQrCodeDataUri("user@example.com", "SECRET123");

        assertThat(dataUri).startsWith("data:image/png;base64,");
        assertThat(dataUri.length()).isGreaterThan("data:image/png;base64,".length());
    }

    @Test
    void verifyCode_validCurrentCode_returnsTrue() throws Exception {
        String secret = this.service.generateSecret();
        long counter = Math.floorDiv(System.currentTimeMillis() / 1000, 30);
        String validCode = new DefaultCodeGenerator(HashingAlgorithm.SHA1).generate(secret, counter);

        assertThat(this.service.verifyCode(secret, validCode)).isTrue();
    }

    @Test
    void verifyCode_wrongCode_returnsFalse() {
        String secret = this.service.generateSecret();

        assertThat(this.service.verifyCode(secret, "000000")).isFalse();
    }

    @Test
    void verifyCode_malformedSecret_returnsFalseRatherThanThrowing() {
        assertThat(this.service.verifyCode("not-valid-base32!!", "123456")).isFalse();
    }

    private static MfaProperties properties() {
        return new MfaProperties("encryption-key", "5a1e2b3c4d5e6f708192a3b4c5d6e7f8", 10, 10, "auth-lava-test", 1);
    }
}
