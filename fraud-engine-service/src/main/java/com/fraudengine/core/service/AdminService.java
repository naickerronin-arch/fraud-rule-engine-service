package com.fraudengine.core.service;

import com.fraudengine.core.controller.model.BadLocationResponse;
import com.fraudengine.core.controller.model.OverrideTransactionRequest;
import com.fraudengine.core.controller.model.TransactionOverrideResponse;
import com.fraudengine.core.exception.FraudEngineErrorMessages;
import com.fraudengine.core.exception.ListBadLocationsException;
import com.fraudengine.core.exception.OverrideTransactionException;
import com.fraudengine.core.exception.TransactionNotFoundException;
import com.fraudengine.core.persistence.entity.BadLocation;
import com.fraudengine.core.persistence.repository.BadLocationRepository;
import com.fraudengine.core.persistence.repository.EvaluatedTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final EvaluatedTransactionRepository evaluatedTransactionRepository;
    private final BadLocationRepository badLocationRepository;

    @Transactional
    public TransactionOverrideResponse overrideTransaction(final String transactionId, final OverrideTransactionRequest request) {
        try {
            evaluatedTransactionRepository.findById(transactionId)
                    .orElseThrow(() -> new TransactionNotFoundException(FraudEngineErrorMessages.TRANSACTION_NOT_FOUND));

            Instant overriddenAt = Instant.now();
            evaluatedTransactionRepository.applyOverride(transactionId, request.getFlagged(), overriddenAt);

            return TransactionOverrideResponse.builder()
                    .transactionId(transactionId)
                    .overriddenFlagged(request.getFlagged())
                    .overriddenAt(overriddenAt)
                    .build();
        } catch (TransactionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error overriding transaction verdict: transactionId={}", transactionId, e);
            throw new OverrideTransactionException(FraudEngineErrorMessages.OVERRIDE_TRANSACTION_FAILED);
        }
    }

    public Page<BadLocationResponse> listBadLocations(final Pageable pageable) {
        try {
            Page<BadLocation> locations = badLocationRepository.findAll(pageable);
            return locations.map(this::mapToBadLocationResponse);
        } catch (Exception e) {
            log.error("Error listing bad locations", e);
            throw new ListBadLocationsException(FraudEngineErrorMessages.LIST_BAD_LOCATIONS_FAILED);
        }
    }

    private BadLocationResponse mapToBadLocationResponse(final BadLocation location) {
        return BadLocationResponse.builder()
                .id(location.getId())
                .areaCode(location.getAreaCode())
                .level(location.getLevel())
                .createdAt(location.getCreatedAt())
                .build();
    }
}
