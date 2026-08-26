package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.TransactionEvaluateRequest;
import com.impulselock.impulselock.model.Decision;
import com.impulselock.impulselock.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transitional, still-unauthenticated endpoint (see docs/v2/tasks.md, Phase 0's "temporarily
 * keep the old unauthenticated V1 endpoints working"). Phase 1 replaces this with an
 * authenticated version under {@code /api/v2/transactions} that resolves the acting user from
 * the JWT instead of a request field (see docs/v2/api-design.md).
 */
@RestController
@RequestMapping("/transaction")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Decision> evaluateTransaction(@RequestBody TransactionEvaluateRequest request) {
        Decision decision = transactionService.evaluateAndSave(request);
        return ResponseEntity.ok(decision);
    }
}
