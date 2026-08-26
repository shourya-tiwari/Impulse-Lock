package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.AuditLogResponseDto;
import com.impulselock.impulselock.dto.PageResponseDto;
import com.impulselock.impulselock.entity.AuditLog;
import com.impulselock.impulselock.mapper.AuditLogMapper;
import com.impulselock.impulselock.service.AuditLogService;
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
 * Endpoint shell (see docs/v2/tasks.md, Phase 3): this reads the {@code audit_log} table
 * correctly, but nothing writes to it yet - expect an empty page until Phase 4's AOP
 * {@code @Auditable} aspect lands. See docs/v2/api-design.md#admin-endpoints.
 */
@RestController
@RequestMapping("/api/v2/admin/audit-logs")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;
    private final AuditLogMapper auditLogMapper;

    public AdminAuditLogController(AuditLogService auditLogService, AuditLogMapper auditLogMapper) {
        this.auditLogService = auditLogService;
        this.auditLogMapper = auditLogMapper;
    }

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
