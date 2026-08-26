package com.impulselock.impulselock.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.repository.RoleRepository;
import com.impulselock.impulselock.repository.UserRepository;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared registration/promotion helpers for Phase 3's controller integration tests, so each test
 * class doesn't reimplement the same MockMvc register-and-extract-token boilerplate that
 * {@code AuthIntegrationTest} already established. {@code AuthIntegrationTest} itself keeps its
 * own richer helper (it also needs the refresh-token cookie, which callers here don't).
 */
public final class MockMvcAuthHelper {

    private MockMvcAuthHelper() {
    }

    public static String registerAndGetAccessToken(MockMvc mockMvc, ObjectMapper objectMapper,
                                                    String username, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v2/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("accessToken");
    }

    public static void promoteToAdmin(UserRepository userRepository, RoleRepository roleRepository, String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.getRoles().add(roleRepository.findByName("ROLE_ADMIN").orElseThrow());
        userRepository.saveAndFlush(user);
    }
}
