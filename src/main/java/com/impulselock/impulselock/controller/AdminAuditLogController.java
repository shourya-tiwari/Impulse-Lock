package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.AuditLogResponseDto;
import com.impulselock.impulselock.dto.PageResponseDto;
import com.impulselock.impulselock.entity.AuditLog;
import com.impulselock.impulselock.mapper.AuditLogMapper;
import com.impulselock.impulselock.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Built as an endpoint shell in Phase 3 (correctly reads {@code audit_log}, but nothing wrote to
 * it yet); Phase 4 added the {@code @Auditable} writer, so this now returns real results for
 * register/login/preference-change/transaction-evaluation/admin actions. See
 * docs/v2/api-design.md#admin-endpoints.
 */
@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v2/admin/audit-logs")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;
    private final AuditLogMapper auditLogMapper;

    public AdminAuditLogController(AuditLogService auditLogService, AuditLogMapper auditLogMapper) {
        this.auditLogService = auditLogService;
        this.auditLogMapper = auditLogMapper;
    }

    @Operation(summary = "Search the audit trail",
            description = "action/from/to are all optional and combine with AND. See "
                    + "docs/v2/database-design.md#audit_log for the action names each phase writes "
                    + "(e.g. USER_REGISTERED, LOGIN_SUCCESS, LOGIN_FAILURE, TRANSACTION_EVALUATED, "
                    + "ADMIN_RULE_CONFIG_CHANGED). This table is append-only - there is no update/delete API.")
    @GetMapping
    public ResponseEntity<PageResponseDto<AuditLogResponseDto>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AuditLog> page = auditLogService.search(action, from, to, pageable);
        return ResponseEntity.ok(PageResponseDto.from(page, auditLogMapper::toResponse));
    }
}
