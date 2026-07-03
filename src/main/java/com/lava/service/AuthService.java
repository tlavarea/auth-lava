package com.lava.service;

import com.lava.exception.EmailAlreadyRegisteredException;
import com.lava.repository.UserRepository;
import com.lava.security.AuthUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    public void register(String email, String rawPassword) {
        if (this.userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        this.userRepository.insert(email, passwordEncoder.encode(rawPassword));
    }

    public AuthUserPrincipal login(
            String email, String rawPassword, HttpServletRequest request, HttpServletResponse response) {
        Authentication authRequest = UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword);
        Authentication authResult = authenticationManager.authenticate(authRequest);
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return (AuthUserPrincipal) authResult.getPrincipal();
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
}
