package com.lreyes.platform.modules.sales;

import com.lreyes.platform.modules.sales.dto.CreateOrderRequest;
import com.lreyes.platform.modules.sales.dto.OrderResponse;
import com.lreyes.platform.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;

    @GetMapping
    public ResponseEntity<PageResponse<OrderResponse>> findAll(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(salesService.findAll(search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(salesService.findById(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salesService.create(request));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<OrderResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(salesService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<OrderResponse> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(salesService.reject(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        salesService.delete(id);
    }
}
