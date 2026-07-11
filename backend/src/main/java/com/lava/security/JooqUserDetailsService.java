package com.lava.security;

import com.lava.model.database.view.AuthUserView;
import com.lava.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class JooqUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AuthUserView user = this.userRepository
                .findAuthUserByEmail(email)
                .filter(u -> !"deleted".equals(u.status()))
                .orElseThrow(() -> new UsernameNotFoundException("No user found for email: " + email));

        return AuthUserPrincipal.from(user);
    }
}
