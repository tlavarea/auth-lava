package com.lava.service;

import com.lava.boot.autoconfigure.app.MailProperties;
import com.lava.logging.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Override
    public void sendVerificationCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(this.mailProperties.fromAddress());
        message.setTo(email);
        message.setSubject("Your verification code");
        message.setText("Your verification code is: " + code + "\n\nThis code expires in a few minutes.");

        this.mailSender.send(message);
        log.info("sendVerificationCode::sent to: {}", LogSanitizer.sanitize(email));
    }
}
