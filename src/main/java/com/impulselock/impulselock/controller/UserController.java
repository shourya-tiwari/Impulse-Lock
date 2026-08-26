package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.RestrictedCategoryRequest;
import com.impulselock.impulselock.dto.UserPreferencesUpdateRequest;
import com.impulselock.impulselock.dto.UserProfileResponse;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.security.SecurityUser;
import com.impulselock.impulselock.service.UserService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Moved from the legacy {@code /users} path (Phase 0/1) to the target V2 path, and split into
 * profile/preferences/restricted-categories endpoints - see
 * docs/v2/api-design.md#userpreferences-endpoints-apiv2users--authenticated. Account creation
 * lives exclusively at {@code POST /api/v2/auth/register} (see AuthController).
 */
@RestController
@RequestMapping("/api/v2/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(new UserProfileResponse(userService.getProfile(principal.getUsername())));
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<UserProfileResponse> updatePreferences(@AuthenticationPrincipal SecurityUser principal,
                                                                   @RequestBody UserPreferencesUpdateRequest request) {
        User updated = userService.updatePreferences(principal.getUsername(), request);
        return ResponseEntity.ok(new UserProfileResponse(updated));
    }

    @GetMapping("/me/restricted-categories")
    public ResponseEntity<List<String>> restrictedCategories(@AuthenticationPrincipal SecurityUser principal) {
        User user = userService.getProfile(principal.getUsername());
        return ResponseEntity.ok(user.getRestrictedCategoryNames());
    }

    @PostMapping("/me/restricted-categories")
    public ResponseEntity<List<String>> addRestrictedCategory(@AuthenticationPrincipal SecurityUser principal,
                                                                @RequestBody RestrictedCategoryRequest request) {
        User updated = userService.addRestrictedCategory(principal.getUsername(), request.getCategory());
        return ResponseEntity.ok(updated.getRestrictedCategoryNames());
    }

    @DeleteMapping("/me/restricted-categories/{category}")
    public ResponseEntity<Void> removeRestrictedCategory(@AuthenticationPrincipal SecurityUser principal,
                                                          @PathVariable String category) {
        userService.removeRestrictedCategory(principal.getUsername(), category);
        return ResponseEntity.noContent().build();
    }
}
