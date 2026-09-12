package com.fraudengine.core.controller;

import com.fraudengine.core.controller.model.BadLocationResponse;
import com.fraudengine.core.controller.model.OverrideTransactionRequest;
import com.fraudengine.core.controller.model.TransactionOverrideResponse;
import com.fraudengine.core.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor // Service for admin operations on Fraud engine
public class AdminController implements AdminInterface {

    private final AdminService adminService;

    @Override
    @PostMapping("/transactions/{id}/override")
    public ResponseEntity<TransactionOverrideResponse> overrideTransaction(
            @PathVariable("id") final String id, @RequestBody final OverrideTransactionRequest request) {
        return ResponseEntity.ok(adminService.overrideTransaction(id, request));
    }

    @Override
    @GetMapping("/locations")
    public ResponseEntity<Page<BadLocationResponse>> listBadLocations(@RequestParam(defaultValue = "0") final int page,
                                                     @RequestParam(defaultValue = "20") final int size) {
        return ResponseEntity.ok(adminService.listBadLocations(PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }
}
