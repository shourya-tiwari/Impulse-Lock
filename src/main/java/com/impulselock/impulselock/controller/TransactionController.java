package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.model.Decision;
import com.impulselock.impulselock.model.Transaction;
import com.impulselock.impulselock.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transaction")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Decision> evaluateTransaction(@RequestBody Transaction transaction) {
        Decision decision = transactionService.evaluateAndSave(transaction);
        return ResponseEntity.ok(decision);
    }
}
