package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.TransactionEvaluateRequest;
import com.impulselock.impulselock.model.Decision;
import com.impulselock.impulselock.security.SecurityUser;
import com.impulselock.impulselock.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Now authenticated (see docs/v2/security-design.md) - the acting user is resolved from the
 * JWT via {@code @AuthenticationPrincipal}, never a request field. Still at its legacy
 * {@code /transaction} path; Phase 3 moves this under {@code /api/v2/transactions} alongside
 * the rest of the API redesign (see docs/v2/api-design.md).
 */
@RestController
@RequestMapping("/transaction")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Decision> evaluateTransaction(@AuthenticationPrincipal SecurityUser principal,
                                                          @RequestBody TransactionEvaluateRequest request) {
        Decision decision = transactionService.evaluateAndSave(principal.getUsername(), request);
        return ResponseEntity.ok(decision);
    }
}
