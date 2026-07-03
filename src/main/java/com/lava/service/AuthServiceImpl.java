package com.lava.service;

import com.lava.exception.EmailAlreadyRegisteredException;
import com.lava.logging.LogSanitizer;
import com.lava.repository.UserRepositoryImpl;
import com.lava.security.AuthUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepositoryImpl userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @Override
    public AuthUserPrincipal login(
            String email, String rawPassword, HttpServletRequest request, HttpServletResponse response) {
        Authentication authRequest = UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword);
        Authentication authResult = this.authenticationManager.authenticate(authRequest);
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        this.securityContextRepository.saveContext(context, request, response);

        return (AuthUserPrincipal) authResult.getPrincipal();
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @Override
    @Transactional
    public void register(String email, String rawPassword) {
        if (this.userRepository.existsByEmail(email)) {
            log.warn("register::email already exists: {}", LogSanitizer.sanitize(email));
            throw new EmailAlreadyRegisteredException(email);
        }

        this.userRepository.insert(email, this.passwordEncoder.encode(rawPassword));
        log.info("register::email registered: {}", LogSanitizer.sanitize(email));
    }
}
