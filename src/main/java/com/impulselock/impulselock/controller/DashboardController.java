package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.DashboardSummaryResponse;
import com.impulselock.impulselock.dto.RiskTrendPointResponse;
import com.impulselock.impulselock.dto.SpendingByCategoryResponse;
import com.impulselock.impulselock.dto.TopTriggeredRuleResponse;
import com.impulselock.impulselock.security.SecurityUser;
import com.impulselock.impulselock.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Dashboard", description = "Aggregated summary/category/risk-trend/top-rules views over the last 30 days")
@RestController
@RequestMapping("/api/v2/dashboard")
public class DashboardController {

    private static final String USER_ID_DESCRIPTION =
            "ROLE_ADMIN only - view another user's dashboard by their numeric id instead of the caller's own. "
                    + "Omit for the caller's own dashboard. Any other role passing this gets 403.";

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Transaction count, total spend, decision breakdown, and average risk score")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(
            @AuthenticationPrincipal SecurityUser principal,
            @Parameter(description = USER_ID_DESCRIPTION) @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(dashboardService.summary(principal, userId));
    }

    @Operation(summary = "Total spend and transaction count grouped by category")
    @GetMapping("/spending-by-category")
    public ResponseEntity<List<SpendingByCategoryResponse>> spendingByCategory(
            @AuthenticationPrincipal SecurityUser principal,
            @Parameter(description = USER_ID_DESCRIPTION) @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(dashboardService.spendingByCategory(principal, userId));
    }

    @Operation(summary = "Daily transaction count and average risk score")
    @GetMapping("/risk-trend")
    public ResponseEntity<List<RiskTrendPointResponse>> riskTrend(
            @AuthenticationPrincipal SecurityUser principal,
            @Parameter(description = USER_ID_DESCRIPTION) @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(dashboardService.riskTrend(principal, userId));
    }

    @Operation(summary = "Which rule codes fired most often, derived from triggered_rules",
            description = "Counts entries from each transaction's structured triggeredRules list "
                    + "(see docs/v1/rule-engine.md for why this is more reliable than parsing the "
                    + "explanation string), sorted by trigger count descending.")
    @GetMapping("/top-triggered-rules")
    public ResponseEntity<List<TopTriggeredRuleResponse>> topTriggeredRules(
            @AuthenticationPrincipal SecurityUser principal,
            @Parameter(description = USER_ID_DESCRIPTION) @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(dashboardService.topTriggeredRules(principal, userId));
    }
}
