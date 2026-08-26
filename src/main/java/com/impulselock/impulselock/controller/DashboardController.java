package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.DashboardSummaryResponse;
import com.impulselock.impulselock.dto.RiskTrendPointResponse;
import com.impulselock.impulselock.dto.SpendingByCategoryResponse;
import com.impulselock.impulselock.dto.TopTriggeredRuleResponse;
import com.impulselock.impulselock.security.SecurityUser;
import com.impulselock.impulselock.service.DashboardService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * New in Phase 3 - see docs/v2/api-design.md#dashboard-endpoints-apiv2dashboard--authenticated-new-in-v2.
 * {@code userId} lets a {@code ROLE_ADMIN} caller view another user's dashboard; anyone else
 * passing it gets a 403 (enforced in {@code DashboardService}, not here).
 */
@RestController
@RequestMapping("/api/v2/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(@AuthenticationPrincipal SecurityUser principal,
                                                              @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(dashboardService.summary(principal, userId));
    }

    @GetMapping("/spending-by-category")
    public ResponseEntity<List<SpendingByCategoryResponse>> spendingByCategory(
            @AuthenticationPrincipal SecurityUser principal, @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(dashboardService.spendingByCategory(principal, userId));
    }

    @GetMapping("/risk-trend")
    public ResponseEntity<List<RiskTrendPointResponse>> riskTrend(@AuthenticationPrincipal SecurityUser principal,
                                                                   @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(dashboardService.riskTrend(principal, userId));
    }

    @GetMapping("/top-triggered-rules")
    public ResponseEntity<List<TopTriggeredRuleResponse>> topTriggeredRules(
            @AuthenticationPrincipal SecurityUser principal, @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(dashboardService.topTriggeredRules(principal, userId));
    }
}
