package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.UserPreferencesUpdateRequest;
import com.impulselock.impulselock.dto.UserProfileResponse;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.security.SecurityUser;
import com.impulselock.impulselock.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account creation moved to {@code POST /api/v2/auth/register} (see AuthController) - this
 * endpoint now only updates preferences on the already-authenticated caller's own account,
 * never creates one. Renamed from V1/Phase 0's {@code POST /users} to
 * {@code PUT /users/me/preferences} since "create or update a user by client-chosen id" no
 * longer describes what this does; Phase 3 moves it under {@code /api/v2/users/me/preferences}
 * (see docs/v2/api-design.md).
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<UserProfileResponse> updatePreferences(@AuthenticationPrincipal SecurityUser principal,
                                                                   @RequestBody UserPreferencesUpdateRequest request) {
        User updated = userService.updatePreferences(principal.getUsername(), request);
        return ResponseEntity.ok(new UserProfileResponse(updated));
    }
}
