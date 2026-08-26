package com.impulselock.impulselock.controller;

import com.impulselock.impulselock.dto.PageResponseDto;
import com.impulselock.impulselock.dto.TransactionEvaluateRequest;
import com.impulselock.impulselock.dto.TransactionHistoryFilter;
import com.impulselock.impulselock.dto.TransactionResponseDto;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.mapper.TransactionMapper;
import com.impulselock.impulselock.model.DecisionType;
import com.impulselock.impulselock.security.SecurityUser;
import com.impulselock.impulselock.service.TransactionService;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Moved from the legacy {@code /transaction} path (Phase 0/1) to the target V2 path, and split
 * V1's single evaluate endpoint into evaluate/get-by-id/history/export - see
 * docs/v2/api-design.md#transaction-endpoints-apiv2transactions--authenticated.
 */
@RestController
@RequestMapping("/api/v2/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    public TransactionController(TransactionService transactionService, TransactionMapper transactionMapper) {
        this.transactionService = transactionService;
        this.transactionMapper = transactionMapper;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<TransactionResponseDto> evaluate(@AuthenticationPrincipal SecurityUser principal,
                                                             @RequestBody TransactionEvaluateRequest request) {
        Transaction transaction = transactionService.evaluateAndSave(principal.getUsername(), request);
        return ResponseEntity.ok(transactionMapper.toResponse(transaction));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<TransactionResponseDto> getByPublicId(@AuthenticationPrincipal SecurityUser principal,
                                                                  @PathVariable String publicId) {
        Transaction transaction = transactionService.getByPublicId(principal, publicId);
        return ResponseEntity.ok(transactionMapper.toResponse(transaction));
    }

    @GetMapping("/history")
    public ResponseEntity<PageResponseDto<TransactionResponseDto>> history(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) DecisionType decisionType,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @PageableDefault(size = 20, sort = "occurredAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {

        TransactionHistoryFilter filter =
                new TransactionHistoryFilter(from, to, category, merchant, decisionType, minAmount, maxAmount);
        Page<Transaction> page = transactionService.search(principal.getUsername(), filter, pageable);

        return ResponseEntity.ok(PageResponseDto.from(page, transactionMapper::toResponse));
    }

    @GetMapping("/history/export")
    public ResponseEntity<StreamingResponseBody> exportHistory(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) DecisionType decisionType,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {

        TransactionHistoryFilter filter =
                new TransactionHistoryFilter(from, to, category, merchant, decisionType, minAmount, maxAmount);
        List<Transaction> transactions = transactionService.searchForExport(principal.getUsername(), filter);

        // Not yet audit-logged as a data-export action (see docs/v2/api-design.md) - the audit
        // writer doesn't exist until Phase 4 (see docs/v2/tasks.md, Phase 3's note).
        StreamingResponseBody body = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write("publicId,amount,category,merchant,occurredAt,decisionType,riskScore,explanation\n");
                for (Transaction transaction : transactions) {
                    writer.write(toCsvRow(transaction));
                }
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transaction-history.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    private String toCsvRow(Transaction transaction) {
        return String.join(",",
                transaction.getPublicId(),
                transaction.getAmount().toPlainString(),
                csvEscape(transaction.getCategory()),
                csvEscape(transaction.getMerchant()),
                transaction.getOccurredAt().toString(),
                transaction.getDecisionType().name(),
                transaction.getRiskScore().toPlainString(),
                csvEscape(transaction.getExplanation())) + "\n";
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
