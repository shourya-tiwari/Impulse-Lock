package com.impulselock.impulselock.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.entity.Role;
import com.impulselock.impulselock.entity.User;
import java.util.HashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * A real {@link JwtService} is used (not mocked) so a genuine signed token exercises the filter's
 * actual parsing path - only {@link SecurityUserDetailsService} is mocked, since it would
 * otherwise need a real DataSource.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private SecurityUserDetailsService userDetailsService;

    private final JwtService jwtService = new JwtService("a-test-signing-secret-that-is-long-enough-for-hmac-sha256", 15);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User user(boolean enabled) {
        User user = new User();
        user.setUsername("alice");
        user.setEnabled(enabled);
        user.setRoles(new HashSet<>(java.util.Set.of(new Role("ROLE_USER"))));
        return user;
    }

    private String tokenFor(User user) {
        return jwtService.generateAccessToken(user);
    }

    @Test
    void aValidTokenForAnEnabledUserPopulatesTheSecurityContext() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        User user = user(true);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(new SecurityUser(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tokenFor(user));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
        assertThat(chain.getRequest()).isNotNull(); // the chain was actually invoked
    }

    @Test
    void aMissingAuthorizationHeaderLeavesTheRequestUnauthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aMalformedTokenLeavesTheRequestUnauthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-real-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aTokenForADisabledUserLeavesTheRequestUnauthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        User disabled = user(false);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(new SecurityUser(disabled));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tokenFor(disabled));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aTokenForANoLongerExistingUserLeavesTheRequestUnauthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        User user = user(true);
        when(userDetailsService.loadUserByUsername("alice"))
                .thenThrow(new UsernameNotFoundException("gone"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tokenFor(user));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void anAlreadyAuthenticatedRequestIsNotReAuthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        var existing = new org.springframework.security.authentication.TestingAuthenticationToken("someone-else", null);
        SecurityContextHolder.getContext().setAuthentication(existing);

        User user = user(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tokenFor(user));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }
}
