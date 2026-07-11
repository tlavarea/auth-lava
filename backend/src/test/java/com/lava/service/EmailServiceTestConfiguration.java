package com.lava.service;

import com.lava.boot.autoconfigure.app.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@Configuration
@Import(MailSenderAutoConfiguration.class)
@EnableConfigurationProperties(MailProperties.class)
@ComponentScan(
        basePackageClasses = EmailServiceImpl.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = EmailServiceImpl.class),
        useDefaultFilters = false)
class EmailServiceTestConfiguration {}
