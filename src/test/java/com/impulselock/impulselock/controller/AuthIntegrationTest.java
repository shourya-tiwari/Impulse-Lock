package com.impulselock.impulselock.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.repository.RoleRepository;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the full auth lifecycle through the real security filter chain (MockMvc against a
 * full {@code @SpringBootTest} context, not {@code @WebMvcTest} with security sliced out - see
 * docs/v2/testing-strategy.md#controller--integration-tests). Covers register/login/refresh
 * (with rotation substituting for "wait 15 minutes for the access token to expire" - rotation
 * invalidating the old refresh token is a more deterministic way to prove the same lifecycle),
 * logout, and role-based 401/403 outcomes per docs/v2/tasks.md Phase 1's test bullet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest extends AbstractIntegrationTest {

    // restrictedCategories is deliberately not part of this request - see
    // UserPreferencesUpdateRequest's Phase 4 cleanup note.
    private static final String PREFERENCES_BODY =
            "{\"dailyLimit\":1500,\"nightSpendingAllowed\":false,\"sensitivityLevel\":6}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void registerCreatesAccountAndReturnsAccessTokenAndRefreshCookie() throws Exception {
        MvcResult result = mockMvc.perform(registerRequest("alice", "alice@example.com", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andReturn();

        assertThat(result.getResponse().getCookie("refreshToken")).isNotNull();
        assertThat(result.getResponse().getCookie("refreshToken").isHttpOnly()).isTrue();
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        mockMvc.perform(registerRequest("bob", "bob@example.com", "password123")).andExpect(status().isOk());

        mockMvc.perform(registerRequest("bob", "different@example.com", "password123"))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithValidCredentialsReturnsTokens() throws Exception {
        mockMvc.perform(registerRequest("carol", "carol@example.com", "password123")).andExpect(status().isOk());

        mockMvc.perform(loginRequest("carol", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(registerRequest("dave", "dave@example.com", "password123")).andExpect(status().isOk());

        mockMvc.perform(loginRequest("dave", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/v2/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PREFERENCES_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithValidTokenSucceeds() throws Exception {
        String accessToken = registerAndExtract("erin", "erin@example.com", "password123").accessToken();

        mockMvc.perform(put("/api/v2/users/me/preferences")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PREFERENCES_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensitivityLevel").value(6));
    }

    @Test
    void protectedEndpointWithTamperedTokenReturns401() throws Exception {
        String accessToken = registerAndExtract("frank", "frank@example.com", "password123").accessToken();
        // Tamper a character in the middle of the signature segment, not the very last character:
        // base64url's final character in an encoding group can have fewer than 6 significant bits,
        // so some substitutions right at the end decode to the exact same byte value and leave the
        // signature - and therefore validity - unchanged, making the test flaky.
        int tamperIndex = accessToken.length() / 2;
        char original = accessToken.charAt(tamperIndex);
        String tampered = accessToken.substring(0, tamperIndex)
                + (original == 'a' ? 'b' : 'a')
                + accessToken.substring(tamperIndex + 1);

        mockMvc.perform(put("/api/v2/users/me/preferences")
                        .header("Authorization", "Bearer " + tampered)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PREFERENCES_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithMissingCookieReturns401() throws Exception {
        mockMvc.perform(post("/api/v2/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndInvalidatesThePreviousOne() throws Exception {
        RegistrationResult registration = registerAndExtract("grace", "grace@example.com", "password123");

        MvcResult refreshResult = mockMvc.perform(
                        post("/api/v2/auth/refresh").cookie(new Cookie("refreshToken", registration.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        assertThat(refreshResult.getResponse().getCookie("refreshToken").getValue())
                .isNotEqualTo(registration.refreshToken());

        // The original refresh token was revoked by rotation - reusing it must now fail. This
        // stands in for "let the access token expire, then refresh" (see class-level note).
        mockMvc.perform(post("/api/v2/auth/refresh").cookie(new Cookie("refreshToken", registration.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        RegistrationResult registration = registerAndExtract("henry", "henry@example.com", "password123");

        mockMvc.perform(post("/api/v2/auth/logout").cookie(new Cookie("refreshToken", registration.refreshToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v2/auth/refresh").cookie(new Cookie("refreshToken", registration.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminOnlyPathForbiddenForRegularUser() throws Exception {
        String accessToken = registerAndExtract("iris", "iris@example.com", "password123").accessToken();

        mockMvc.perform(get("/api/v2/admin/anything").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOnlyPathIsNotForbiddenForAdminRole() throws Exception {
        String accessToken = registerAndExtract("judy", "judy@example.com", "password123").accessToken();
        promoteToAdmin("judy");

        // No admin controller exists until Phase 3, so this 404s (Spring's default "no
        // handler found") rather than 200 - the point of this test is that the security layer
        // does NOT block it with 403, proving the hasRole("ADMIN") rule actually passes for an
        // admin. See SecurityConfig's class-level note.
        mockMvc.perform(get("/api/v2/admin/anything").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    private void promoteToAdmin(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.getRoles().add(roleRepository.findByName("ROLE_ADMIN").orElseThrow());
        userRepository.saveAndFlush(user);
    }

    private RegistrationResult registerAndExtract(String username, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(registerRequest(username, email, password))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        String accessToken = (String) body.get("accessToken");
        String refreshToken = result.getResponse().getCookie("refreshToken").getValue();
        return new RegistrationResult(accessToken, refreshToken);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registerRequest(
            String username, String email, String password) throws Exception {
        return post("/api/v2/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "email", email,
                        "password", password)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String username, String password) throws Exception {
        return post("/api/v2/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "password", password)));
    }

    private record RegistrationResult(String accessToken, String refreshToken) {
    }
}
