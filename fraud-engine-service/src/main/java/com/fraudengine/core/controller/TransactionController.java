package com.fraudengine.core.controller;

import com.fraudengine.core.controller.model.EvaluatedTransactionResponse;
import com.fraudengine.core.controller.model.TransactionDetailResponse;
import com.fraudengine.core.service.TransactionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController implements TransactionInterface {

    private final TransactionQueryService transactionQueryService;

    @Override
    @GetMapping
    public ResponseEntity<Page<EvaluatedTransactionResponse>> list(@RequestParam(required = false) final String accountNumber,
                                        @RequestParam(required = false) final String ruleType,
                                        @RequestParam(required = false) final String status,
                                        @RequestParam(defaultValue = "0") final int page,
                                        @RequestParam(defaultValue = "20") final int size) {
        return ResponseEntity.ok(
                transactionQueryService.list(accountNumber, ruleType, status, PageRequest.of(page, size)));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<TransactionDetailResponse> get(@PathVariable final String id) {
        return ResponseEntity.ok(transactionQueryService.get(id));
    }
}
