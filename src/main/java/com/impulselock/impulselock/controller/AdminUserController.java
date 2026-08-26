package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.AdminUserStatusUpdateRequest;
import com.impulselock.impulselock.dto.PageResponseDto;
import com.impulselock.impulselock.dto.UserProfileResponse;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * New in Phase 3 - see docs/v2/api-design.md#admin-endpoints-apiv2admin--role_admin-only.
 * {@code /api/v2/admin/**} is already restricted to {@code ROLE_ADMIN} at the URL level by
 * {@code SecurityConfig} (added ahead of any admin controller back in Phase 1), so no
 * additional {@code @PreAuthorize} is needed here.
 */
@Tag(name = "Admin", description = "ROLE_ADMIN only - user management, rule-config tuning, audit log viewing")
@RestController
@RequestMapping("/api/v2/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDto<UserProfileResponse>> findAll(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<User> page = adminUserService.findAll(pageable);
        return ResponseEntity.ok(PageResponseDto.from(page, UserProfileResponse::new));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(new UserProfileResponse(adminUserService.findById(id)));
    }

    @Operation(summary = "Enable or disable a user account",
            description = "Takes effect on the target's very next request with their still-live "
                    + "access token - JwtAuthenticationFilter re-checks isEnabled() via a fresh DB "
                    + "lookup every time, not just at login.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserProfileResponse> updateStatus(@PathVariable Long id,
                                                             @RequestBody AdminUserStatusUpdateRequest request) {
        User updated = adminUserService.updateStatus(id, request.isEnabled());
        return ResponseEntity.ok(new UserProfileResponse(updated));
    }
}
