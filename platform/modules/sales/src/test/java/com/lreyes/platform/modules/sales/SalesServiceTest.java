package com.lreyes.platform.modules.sales;

import com.lreyes.platform.core.events.DomainEventPublisher;
import com.lreyes.platform.core.workflow.WorkflowService;
import com.lreyes.platform.modules.sales.dto.CreateOrderRequest;
import com.lreyes.platform.modules.sales.dto.OrderLineRequest;
import com.lreyes.platform.modules.sales.dto.OrderResponse;
import com.lreyes.platform.modules.sales.event.OrderCreatedEvent;
import com.lreyes.platform.shared.domain.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock
    private SalesOrderRepository orderRepository;
    @Mock
    private SalesOrderMapper orderMapper;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private WorkflowService workflowService;

    @InjectMocks
    private SalesService salesService;

    private SalesOrder order;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        order = new SalesOrder();
        order.setId(orderId);
        order.setOrderNumber("ORD-001");
        order.setCustomerName("Acme Corp");
        order.setSeller("vendedor1");
        order.setTotalAmount(new BigDecimal("15000.00"));
        order.setStatus(OrderStatus.PENDING_APPROVAL);
        order.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("create - crea pedido, inicia workflow y publica evento")
    void create_savesStartsWorkflowAndPublishesEvent() {
        var lineReq = new OrderLineRequest("Producto A", 2, new BigDecimal("5000.00"));
        var request = new CreateOrderRequest("ORD-001", "Acme Corp", "vendedor1", "Desc", List.of(lineReq));

        var newOrder = new SalesOrder();
        newOrder.setOrderNumber("ORD-001");
        newOrder.setCustomerName("Acme Corp");
        newOrder.setSeller("vendedor1");

        var line = new OrderLine();
        line.setProductName("Producto A");
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("5000.00"));

        when(orderMapper.toEntity(request)).thenReturn(newOrder);
        when(orderMapper.toLineEntity(lineReq)).thenReturn(line);
        when(orderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> {
            SalesOrder o = inv.getArgument(0);
            o.setId(orderId);
            return o;
        });
        when(workflowService.startProcess(eq("venta-aprobacion"), any(), eq("ORD-001"), any()))
                .thenReturn("proc-123");

        var response = new OrderResponse(orderId, "ORD-001", "Acme Corp", "vendedor1",
                "Desc", new BigDecimal("10000.00"), OrderStatus.PENDING_APPROVAL,
                "proc-123", List.of(), Instant.now(), null);
        when(orderMapper.toResponse(any(SalesOrder.class))).thenReturn(response);

        OrderResponse result = salesService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.orderNumber()).isEqualTo("ORD-001");
        verify(workflowService).startProcess(eq("venta-aprobacion"), any(), eq("ORD-001"), any());

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventPublisher).publish(captor.capture(), eq("sales-order"), eq(orderId));
        assertThat(captor.getValue().getOrderNumber()).isEqualTo("ORD-001");
        assertThat(captor.getValue().eventType()).isEqualTo("order.created");
    }

    @Test
    @DisplayName("findById - retorna pedido encontrado")
    void findById_found() {
        var response = new OrderResponse(orderId, "ORD-001", "Acme Corp", "vendedor1",
                null, new BigDecimal("15000.00"), OrderStatus.PENDING_APPROVAL,
                null, List.of(), Instant.now(), null);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);

        OrderResponse result = salesService.findById(orderId);

        assertThat(result.id()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("findById - lanza excepción si no existe")
    void findById_notFound() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salesService.findById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("approve - cambia estado a APPROVED")
    void approve_changesStatus() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        var response = new OrderResponse(orderId, "ORD-001", "Acme Corp", null,
                null, new BigDecimal("15000.00"), OrderStatus.APPROVED,
                null, List.of(), Instant.now(), null);
        when(orderMapper.toResponse(order)).thenReturn(response);

        OrderResponse result = salesService.approve(orderId);

        assertThat(result.status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
    }

    @Test
    @DisplayName("reject - cambia estado a REJECTED")
    void reject_changesStatus() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        var response = new OrderResponse(orderId, "ORD-001", "Acme Corp", null,
                null, new BigDecimal("15000.00"), OrderStatus.REJECTED,
                null, List.of(), Instant.now(), null);
        when(orderMapper.toResponse(order)).thenReturn(response);

        OrderResponse result = salesService.reject(orderId);

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    @DisplayName("delete - elimina pedido existente")
    void delete_existing() {
        when(orderRepository.existsById(orderId)).thenReturn(true);

        salesService.delete(orderId);

        verify(orderRepository).deleteById(orderId);
    }
}
