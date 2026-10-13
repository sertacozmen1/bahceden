package com.bahceden.order;

import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.common.web.PageResponse;
import com.bahceden.order.domain.Order;
import com.bahceden.order.domain.SellerOrder;
import com.bahceden.order.domain.SellerOrderStatus;
import com.bahceden.order.dto.OrderDetail;
import com.bahceden.order.dto.OrderSummary;
import com.bahceden.order.dto.SellerOrderDetail;
import com.bahceden.order.dto.SellerOrderSummary;
import com.bahceden.seller.SellerService;
import com.bahceden.shipping.ShipmentService;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siparişlerin görüntülenmesi (orders spec'i, add-cart-and-checkout D6).
 *
 * <p>Alıcı yalnızca kendi siparişlerini, satıcı yalnızca kendi ödenmiş satıcı
 * siparişlerini görür; sahiplik sorgu seviyesindedir (ADR-007), satıcı önce
 * onaylı satıcı kapısından geçer (ADR-044). Başkasının kaydı 404 döner.
 */
@Service
public class OrderService {

    static final int MAX_PAGE_SIZE = 50;

    /** Satıcının görebildiği durumlar: ödemesi tamamlanmış satıcı siparişleri (add-shipments D2). */
    static final Set<SellerOrderStatus> SELLER_VISIBLE = EnumSet.complementOf(
            EnumSet.of(SellerOrderStatus.PENDING_PAYMENT, SellerOrderStatus.VOID));

    private final OrderRepository orders;
    private final SellerOrderRepository sellerOrders;
    private final SellerService sellers;
    private final OrderMapper mapper;
    private final ShipmentService shipments;
    private final DefectClaimRepository claims;
    private final Clock clock;

    OrderService(OrderRepository orders, SellerOrderRepository sellerOrders, SellerService sellers, OrderMapper mapper,
                 ShipmentService shipments, DefectClaimRepository claims, Clock clock) {
        this.orders = orders;
        this.sellerOrders = sellerOrders;
        this.sellers = sellers;
        this.mapper = mapper;
        this.shipments = shipments;
        this.claims = claims;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> listOwn(long accountId, int page, int size) {
        return PageResponse.from(orders.findOwnNewestFirst(accountId, PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)))
                .map(mapper::toSummary));
    }

    /** @throws ApiException {@code ORDER_NOT_FOUND} */
    @Transactional(readOnly = true)
    public OrderDetail detail(long accountId, String orderNumber) {
        return detailOf(orders.findByOrderNumberAndBuyerAccountId(orderNumber, accountId).orElseThrow(OrderService::orderNotFound));
    }

    /**
     * Satıcının gelen siparişleri; durumla süzülebilir.
     *
     * @param status satıcının görebildiği durumlardan biri; boşsa hepsi
     */
    @Transactional(readOnly = true)
    public PageResponse<SellerOrderSummary> listForSeller(long accountId, Optional<SellerOrderStatus> status, int page,
                                                          int size) {
        var seller = sellers.requireApprovedSeller(accountId);
        var statuses = status.filter(SELLER_VISIBLE::contains).map(EnumSet::of).orElse(EnumSet.copyOf(SELLER_VISIBLE));
        var now = clock.instant();
        return PageResponse.from(sellerOrders.findForSeller(seller.getId(), statuses,
                        PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)))
                .map(sellerOrder -> mapper.toSellerSummary(sellerOrder, now)));
    }

    /** @throws ApiException {@code SELLER_ORDER_NOT_FOUND} */
    @Transactional(readOnly = true)
    public SellerOrderDetail detailForSeller(long accountId, long sellerOrderId) {
        var seller = sellers.requireApprovedSeller(accountId);
        var sellerOrder = sellerOrders.findOwnBySeller(sellerOrderId, seller.getId(), SELLER_VISIBLE)
                .orElseThrow(OrderService::sellerOrderNotFound);
        var claim = claims.findWithDetailsForSellerOrders(List.of(sellerOrder.getId())).stream().findFirst();
        return mapper.toSellerDetail(sellerOrder, shipments.activeView(sellerOrder.getId()), claim, clock.instant());
    }

    /** Satıcılar ve satırlar tek sorguda, gönderiler ve talepler birer sorguda (sipariş zaten okunmuş). */
    OrderDetail detailOf(Order order) {
        var parts = sellerOrders.findForOrder(order.getId());
        var ids = parts.stream().map(SellerOrder::getId).toList();
        var claimsBySellerOrder = claims.findWithDetailsForSellerOrders(ids).stream()
                .collect(Collectors.toMap(claim -> claim.getSellerOrder().getId(), Function.identity()));
        return mapper.toDetail(order, parts, shipments.activeViews(ids), claimsBySellerOrder, clock.instant());
    }

    static ApiException orderNotFound() {
        return new ApiException(ApiErrorCode.ORDER_NOT_FOUND, "error.orderNotFound");
    }

    static ApiException sellerOrderNotFound() {
        return new ApiException(ApiErrorCode.SELLER_ORDER_NOT_FOUND, "error.sellerOrderNotFound");
    }
}
