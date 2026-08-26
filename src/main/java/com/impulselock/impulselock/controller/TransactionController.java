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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "Transactions", description = "Evaluate, look up, browse (with filters/pagination), and export the caller's own transactions")
@RestController
@RequestMapping("/api/v2/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    public TransactionController(TransactionService transactionService, TransactionMapper transactionMapper) {
        this.transactionService = transactionService;
        this.transactionMapper = transactionMapper;
    }

    @Operation(summary = "Evaluate and persist a transaction for the caller",
            description = "Runs the rule engine against the caller's own preferences and recent "
                    + "transaction history, persists the result, and returns the decision. There is "
                    + "no userId field - the acting user is always resolved from the access token.")
    @PostMapping("/evaluate")
    public ResponseEntity<TransactionResponseDto> evaluate(@AuthenticationPrincipal SecurityUser principal,
                                                             @Valid @RequestBody TransactionEvaluateRequest request) {
        Transaction transaction = transactionService.evaluateAndSave(principal.getUsername(), request);
        return ResponseEntity.ok(transactionMapper.toResponse(transaction));
    }

    @Operation(summary = "Get one transaction by its public ID",
            description = "Owner or ROLE_ADMIN only. A transaction that exists but belongs to "
                    + "someone else returns 404, the same as an unknown publicId - never 403 - so "
                    + "the response never confirms another user's transaction exists (see "
                    + "docs/v2/api-design.md#error-format).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found and owned by the caller (or caller is ROLE_ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Unknown publicId, or it belongs to a different, non-admin-viewed user")
    })
    @GetMapping("/{publicId}")
    public ResponseEntity<TransactionResponseDto> getByPublicId(@AuthenticationPrincipal SecurityUser principal,
                                                                  @PathVariable String publicId) {
        Transaction transaction = transactionService.getByPublicId(principal, publicId);
        return ResponseEntity.ok(transactionMapper.toResponse(transaction));
    }

    @Operation(summary = "Browse the caller's own transaction history",
            description = "All filters are optional and combine with AND. Standard Spring Data "
                    + "page/size/sort query params also apply (e.g. sort=occurredAt,desc).")
    @GetMapping("/history")
    public ResponseEntity<PageResponseDto<TransactionResponseDto>> history(
            @AuthenticationPrincipal SecurityUser principal,
            @Parameter(description = "ISO-8601 date-time, inclusive lower bound on occurredAt")
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

    @Operation(summary = "Export the caller's filtered transaction history as CSV",
            description = "Same filters as /history, but unpaginated and capped at 10,000 rows, "
                    + "streamed rather than buffered in memory. Recorded as a "
                    + "TRANSACTION_HISTORY_EXPORTED audit_log entry on every call.")
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

        // Audited via @Auditable on TransactionService.searchForExport (see Phase 4).
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
