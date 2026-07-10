package com.lava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = EmailServiceTestConfiguration.class)
@ActiveProfiles("test")
class EmailServiceImplTest {

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MailpitTestContainer.INSTANCE::getHost);
        registry.add(
                "spring.mail.port", () -> MailpitTestContainer.INSTANCE.getMappedPort(MailpitTestContainer.SMTP_PORT));
    }

    private final MailpitClient mailpit = new MailpitClient("http://"
            + MailpitTestContainer.INSTANCE.getHost()
            + ":"
            + MailpitTestContainer.INSTANCE.getMappedPort(MailpitTestContainer.HTTP_PORT));

    @Autowired
    private EmailService emailService;

    @AfterEach
    void clearMailbox() {
        this.mailpit.deleteAllMessages();
    }

    @Test
    void sendVerificationCode_deliversEmailWithCodeToRecipient() {
        this.emailService.sendVerificationCode("someone@example.com", "123456");

        MailpitMessage message = this.awaitSingleMessage();

        assertThat(message.from().address()).isEqualTo("no-reply@auth-lava.local");
        assertThat(message.to()).extracting(MailpitMessage.Address::address).containsExactly("someone@example.com");
        assertThat(message.subject()).isEqualTo("Your verification code");

        MailpitMessageDetail detail = this.mailpit.getMessage(message.id());
        assertThat(detail.text()).contains("123456").contains("expires");
    }

    @Test
    void sendVerificationCode_doesNotLeakCodeAcrossRecipients() {
        this.emailService.sendVerificationCode("first@example.com", "111111");
        this.emailService.sendVerificationCode("second@example.com", "222222");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(this.mailpit.listMessages()).hasSize(2));

        List<MailpitMessage> messages = this.mailpit.listMessages();
        MailpitMessage first = messages.stream()
                .filter(m -> m.to().stream().anyMatch(a -> "first@example.com".equals(a.address())))
                .findFirst()
                .orElseThrow();
        MailpitMessage second = messages.stream()
                .filter(m -> m.to().stream().anyMatch(a -> "second@example.com".equals(a.address())))
                .findFirst()
                .orElseThrow();

        assertThat(this.mailpit.getMessage(first.id()).text())
                .contains("111111")
                .doesNotContain("222222");
        assertThat(this.mailpit.getMessage(second.id()).text())
                .contains("222222")
                .doesNotContain("111111");
    }

    private MailpitMessage awaitSingleMessage() {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(this.mailpit.listMessages()).hasSize(1));
        return this.mailpit.listMessages().getFirst();
    }
}
