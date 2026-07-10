package com.lava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.lava.boot.autoconfigure.app.PasswordBreachProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PasswordBreachCheckServiceImplTest {

    // SHA-1("correcthorsebatterystaple") = BFD3617727EAB0E800E62A776C76381DEFBC4145
    private static final String TEST_PASSWORD = "correcthorsebatterystaple";
    private static final String PREFIX = "BFD36";
    private static final String SUFFIX = "17727EAB0E800E62A776C76381DEFBC4145";

    @Test
    void isBreached_suffixPresentInResponse_returnsTrue() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.pwnedpasswords.com/range/" + PREFIX))
                .andExpect(header("Add-Padding", "true"))
                .andRespond(withSuccess("OTHERSUFFIX1:1\n" + SUFFIX + ":42\nOTHERSUFFIX2:3", MediaType.TEXT_PLAIN));

        PasswordBreachCheckServiceImpl service = new PasswordBreachCheckServiceImpl(builder, properties(true));

        assertThat(service.isBreached(TEST_PASSWORD)).isTrue();
        server.verify();
    }

    @Test
    void isBreached_suffixAbsentFromResponse_returnsFalse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.pwnedpasswords.com/range/" + PREFIX))
                .andRespond(withSuccess("OTHERSUFFIX1:1\nOTHERSUFFIX2:3", MediaType.TEXT_PLAIN));

        PasswordBreachCheckServiceImpl service = new PasswordBreachCheckServiceImpl(builder, properties(true));

        assertThat(service.isBreached(TEST_PASSWORD)).isFalse();
    }

    @Test
    void isBreached_requestFails_returnsFalseRatherThanThrowing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.pwnedpasswords.com/range/" + PREFIX))
                .andRespond(withServerError());

        PasswordBreachCheckServiceImpl service = new PasswordBreachCheckServiceImpl(builder, properties(true));

        assertThat(service.isBreached(TEST_PASSWORD)).isFalse();
    }

    @Test
    void isBreached_disabled_returnsFalseWithoutCallingApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        PasswordBreachCheckServiceImpl service = new PasswordBreachCheckServiceImpl(builder, properties(false));

        assertThat(service.isBreached(TEST_PASSWORD)).isFalse();
        server.verify();
    }

    private static PasswordBreachProperties properties(boolean enabled) {
        return new PasswordBreachProperties(enabled);
    }
}
