package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.RuleConfigResponseDto;
import com.impulselock.impulselock.dto.RuleConfigUpdateRequest;
import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.mapper.RuleConfigMapper;
import com.impulselock.impulselock.service.RuleConfigService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * New in Phase 3 - wires up {@code RuleConfigService.update} (built in Phase 2 with no caller
 * yet) to a real admin endpoint. See docs/v2/api-design.md#admin-endpoints.
 */
@RestController
@RequestMapping("/api/v2/admin/rule-configs")
public class AdminRuleConfigController {

    private final RuleConfigService ruleConfigService;
    private final RuleConfigMapper ruleConfigMapper;

    public AdminRuleConfigController(RuleConfigService ruleConfigService, RuleConfigMapper ruleConfigMapper) {
        this.ruleConfigService = ruleConfigService;
        this.ruleConfigMapper = ruleConfigMapper;
    }

    @GetMapping
    public ResponseEntity<List<RuleConfigResponseDto>> findAll() {
        List<RuleConfigResponseDto> configs = ruleConfigService.findAll().stream()
                .map(ruleConfigMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(configs);
    }

    @PutMapping("/{ruleCode}")
    public ResponseEntity<RuleConfigResponseDto> update(@PathVariable String ruleCode,
                                                         @RequestBody RuleConfigUpdateRequest request) {
        RuleConfig updated = ruleConfigService.update(ruleCode, request.getWeight(), request.isEnabled(), request.getParams());
        return ResponseEntity.ok(ruleConfigMapper.toResponse(updated));
    }
}
