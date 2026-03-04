package com.lreyes.platform.modules.sales;

import com.lreyes.platform.core.events.DomainEventPublisher;
import com.lreyes.platform.core.tenancy.TenantContext;
import com.lreyes.platform.core.workflow.WorkflowService;
import com.lreyes.platform.modules.sales.dto.CreateOrderRequest;
import com.lreyes.platform.modules.sales.dto.OrderLineRequest;
import com.lreyes.platform.modules.sales.dto.OrderResponse;
import com.lreyes.platform.modules.sales.event.OrderCreatedEvent;
import com.lreyes.platform.shared.domain.EntityNotFoundException;
import com.lreyes.platform.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SalesService {

    private final SalesOrderRepository orderRepository;
    private final SalesOrderMapper orderMapper;
    private final DomainEventPublisher eventPublisher;
    private final WorkflowService workflowService;

    public PageResponse<OrderResponse> findAll(String search, Pageable pageable) {
        var page = (search != null && !search.isBlank())
                ? orderRepository.findByCustomerNameContainingIgnoreCase(search, pageable)
                : orderRepository.findAll(pageable);
        return PageResponse.from(page, orderMapper::toResponse);
    }

    public OrderResponse findById(UUID id) {
        SalesOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SalesOrder", id));
        return orderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        SalesOrder order = orderMapper.toEntity(request);

        // Crear líneas y calcular total
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLineRequest lineReq : request.lines()) {
            OrderLine line = orderMapper.toLineEntity(lineReq);
            line.setLineTotal(lineReq.unitPrice().multiply(BigDecimal.valueOf(lineReq.quantity())));
            total = total.add(line.getLineTotal());
            order.addLine(line);
        }
        order.setTotalAmount(total);
        order.setStatus(OrderStatus.PENDING_APPROVAL);

        SalesOrder saved = orderRepository.save(order);

        // Iniciar proceso de aprobación en Flowable
        String tenantId = TenantContext.getCurrentTenant();
        String processId = workflowService.startProcess(
                "venta-aprobacion",
                tenantId,
                saved.getOrderNumber(),
                Map.of(
                        "orderId", saved.getId().toString(),
                        "orderNumber", saved.getOrderNumber(),
                        "cliente", saved.getCustomerName(),
                        "vendedor", saved.getSeller() != null ? saved.getSeller() : "",
                        "monto", saved.getTotalAmount().doubleValue(),
                        "descripcion", saved.getDescription() != null ? saved.getDescription() : ""
                ));
        saved.setProcessInstanceId(processId);
        orderRepository.save(saved);

        log.info("Pedido {} creado con proceso de aprobación {}", saved.getOrderNumber(), processId);

        // Publicar evento de dominio
        eventPublisher.publish(
                new OrderCreatedEvent(
                        tenantId,
                        saved.getId(),
                        saved.getOrderNumber(),
                        saved.getTotalAmount()),
                "sales-order",
                saved.getId());

        return orderMapper.toResponse(saved);
    }

    @Transactional
    public OrderResponse approve(UUID id) {
        SalesOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SalesOrder", id));
        order.setStatus(OrderStatus.APPROVED);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse reject(UUID id) {
        SalesOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SalesOrder", id));
        order.setStatus(OrderStatus.REJECTED);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public void delete(UUID id) {
        if (!orderRepository.existsById(id)) {
            throw new EntityNotFoundException("SalesOrder", id);
        }
        orderRepository.deleteById(id);
    }
}
