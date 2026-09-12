package com.fraudengine.core.service;

import com.fraudengine.core.controller.model.EvaluatedTransactionResponse;
import com.fraudengine.core.controller.model.RuleHitResponse;
import com.fraudengine.core.controller.model.TransactionDetailResponse;
import com.fraudengine.core.exception.FetchTransactionDetailsException;
import com.fraudengine.core.exception.FraudEngineErrorMessages;
import com.fraudengine.core.exception.ListTransactionsException;
import com.fraudengine.core.exception.TransactionNotFoundException;
import com.fraudengine.core.persistence.entity.EvaluatedTransaction;
import com.fraudengine.core.persistence.entity.RuleHit;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import com.fraudengine.core.persistence.repository.RuleHitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final EvaluatedTransactionRepository evaluatedTransactionRepository;
    private final RuleHitRepository ruleHitRepository;

    public Page<EvaluatedTransactionResponse> list(final String accountNumber, final String ruleType, final String status, final Pageable pageable) {
        try {
            Page<EvaluatedTransaction> transactions = evaluatedTransactionRepository.search(accountNumber, ruleType, mapStatusToFlagged(status), pageable);
            return transactions.map(this::mapToEvaluatedTransactionResponse);
        } catch (Exception e) {
            log.error("Error listing transactions: accountNumber={}, ruleType={}, status={}", accountNumber, ruleType, status, e);
            throw new ListTransactionsException(FraudEngineErrorMessages.LIST_TRANSACTIONS_FAILED);
        }
    }

    public TransactionDetailResponse get(final String id) {
        try {
            EvaluatedTransaction transaction = evaluatedTransactionRepository.findById(id)
                    .orElseThrow(() -> new TransactionNotFoundException(FraudEngineErrorMessages.TRANSACTION_NOT_FOUND));

            var ruleHits = ruleHitRepository.findByTransactionId(id);
            var ruleHitResponses = ruleHits.stream()
                    .map(this::mapToRuleHitResponse)
                    .collect(Collectors.toList());

            return TransactionDetailResponse.builder()
                    .transaction(mapToEvaluatedTransactionResponse(transaction))
                    .ruleHits(ruleHitResponses)
                    .build();
        } catch (TransactionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching transaction details: id={}", id, e);
            throw new FetchTransactionDetailsException(FraudEngineErrorMessages.FETCH_TRANSACTION_DETAILS_FAILED);
        }
    }

    private EvaluatedTransactionResponse mapToEvaluatedTransactionResponse(final EvaluatedTransaction transaction) {
        return EvaluatedTransactionResponse.builder()
                .transactionId(transaction.getId())
                .accountNumber(transaction.getAccountNumber())
                .amount(transaction.getAmount())
                .transactionType(transaction.getTransactionType())
                .areaCode(transaction.getAreaCode())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private RuleHitResponse mapToRuleHitResponse(final RuleHit hit) {
        return RuleHitResponse.builder()
                .ruleType(hit.getRuleType().name())
                .status(hit.getStatus())
                .flagged(hit.isFlagged())
                .riskLevel(hit.getRiskLevel())
                .evaluatedAt(hit.getEvaluatedAt())
                .build();
    }
    private Boolean mapStatusToFlagged(final String status) {
        if (status == null) {
            return null;
        }
        return switch (status.toUpperCase()) {
            case "FLAGGED" -> Boolean.TRUE;
            case "CLEAR" -> Boolean.FALSE;
            default -> null;
        };
    }
}
