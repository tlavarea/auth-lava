package com.lava.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lava.model.database.view.AuthUserView;
import com.lava.model.database.view.AuthUserViewBuilder;
import com.lava.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class JooqUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private JooqUserDetailsService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        this.service = new JooqUserDetailsService(this.userRepository);
    }

    @Test
    void loadUserByUsername_activeUser_returnsPrincipal() {
        when(this.userRepository.findAuthUserByEmail("user@example.com")).thenReturn(Optional.of(view("active")));

        UserDetails details = this.service.loadUserByUsername("user@example.com");

        assertThat(details.getUsername()).isEqualTo("user@example.com");
    }

    @Test
    void loadUserByUsername_deletedUser_throwsUsernameNotFoundException() {
        when(this.userRepository.findAuthUserByEmail("gone@example.com")).thenReturn(Optional.of(view("deleted")));

        assertThatThrownBy(() -> this.service.loadUserByUsername("gone@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_notFound_throwsUsernameNotFoundException() {
        when(this.userRepository.findAuthUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private static AuthUserView view(String status) {
        return AuthUserViewBuilder.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .status(status)
                .emailVerified(true)
                .roles(Set.of())
                .permissions(Set.of())
                .build();
    }
}
