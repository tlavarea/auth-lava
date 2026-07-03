package com.lava.security;

import com.lava.model.database.view.AuthUserView;
import com.lava.repository.UserRepository;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class JooqUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public JooqUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AuthUserView user = userRepository
                .findAuthUserByEmail(email)
                .filter(u -> !"deleted".equals(u.status()))
                .orElseThrow(() -> new UsernameNotFoundException("No user found for email: " + email));

        Set<GrantedAuthority> authorities = new HashSet<>();
        for (String role : user.roles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
        }
        for (String permission : user.permissions()) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }

        return new AuthUserPrincipal(
                user.id(), user.email(), user.passwordHash(), user.status(), user.emailVerified(), authorities);
    }
}
