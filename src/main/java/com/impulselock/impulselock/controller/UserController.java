package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.UserProfileResponse;
import com.impulselock.impulselock.dto.UserUpsertRequest;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transitional, still-unauthenticated endpoint (see docs/v2/tasks.md, Phase 0's "temporarily
 * keep the old unauthenticated V1 endpoints working"). Phase 1 replaces user creation with a
 * real {@code /api/v2/auth/register}, and Phase 3 splits preference updates into granular
 * endpoints (see docs/v2/api-design.md).
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserProfileResponse> upsertUser(@RequestBody UserUpsertRequest request) {
        User saved = userService.upsertUser(request);
        return ResponseEntity.ok(new UserProfileResponse(saved));
    }
}
