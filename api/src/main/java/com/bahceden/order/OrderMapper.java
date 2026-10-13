package com.bahceden.order;

import com.bahceden.catalog.ProductMapper;
import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.geo.TurkishProvinces;
import com.bahceden.order.domain.DefectClaim;
import com.bahceden.order.domain.DefectClaimTransition;
import com.bahceden.order.domain.Order;
import com.bahceden.order.domain.OrderItem;
import com.bahceden.order.domain.SellerOrder;
import com.bahceden.order.domain.SellerOrderStatus;
import com.bahceden.order.domain.SellerOrderTransition;
import com.bahceden.order.dto.AdminClaimDetail;
import com.bahceden.order.dto.AdminClaimSummary;
import com.bahceden.order.dto.AdminSellerOrderDetail;
import com.bahceden.order.dto.AdminSellerOrderSummary;
import com.bahceden.order.dto.BuyerSellerOrderView;
import com.bahceden.order.dto.ClaimSettlementView;
import com.bahceden.order.dto.DefectClaimView;
import com.bahceden.order.dto.OrderDetail;
import com.bahceden.order.dto.OrderItemView;
import com.bahceden.order.dto.OrderSummary;
import com.bahceden.order.dto.SellerOrderDetail;
import com.bahceden.order.dto.SellerOrderSummary;
import com.bahceden.order.dto.ShippingAddressView;
import com.bahceden.shipping.dto.ShipmentView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Sipariş entity'lerinden DTO'lara dönüşüm; elle yazılır (ADR-002).
 *
 * <p>Alıcı görünümünde komisyon ve hakediş yoktur; satıcı görünümünde alıcının
 * adresinden yalnızca il ve ilçe vardır (orders spec'i, K10).
 */
@Component
class OrderMapper {

    private final ProductMapper productMapper;
    private final BahcedenProperties properties;

    OrderMapper(ProductMapper productMapper, BahcedenProperties properties) {
        this.productMapper = productMapper;
        this.properties = properties;
    }

    OrderSummary toSummary(Order order) {
        return new OrderSummary(order.getOrderNumber(), order.getStatus(), money(order, order.getGrandTotal()),
                order.getCreatedAt());
    }

    /**
     * @param shipments satıcı siparişi kimliğine göre etkin gönderiler
     * @param claims    satıcı siparişi kimliğine göre ayıplı ürün talepleri
     */
    OrderDetail toDetail(Order order, List<SellerOrder> sellerOrders, Map<Long, ShipmentView> shipments,
                         Map<Long, DefectClaim> claims, Instant now) {
        var address = order.getShippingAddress();
        var shipping = new ShippingAddressView(address.recipientName(), address.phone(), address.provinceCode(),
                provinceName(address.provinceCode()), address.district(), address.addressLine(), address.postalCode());
        return new OrderDetail(order.getOrderNumber(), order.getStatus(), order.getCreatedAt(),
                order.getPaidAt().orElse(null), shipping, money(order, order.getItemsTotal()),
                money(order, order.getShippingTotal()), money(order, order.getGrandTotal()),
                sellerOrders.stream()
                        .map(sellerOrder -> toBuyerView(order, sellerOrder, shipments.get(sellerOrder.getId()),
                                claims.get(sellerOrder.getId()), now))
                        .toList());
    }

    SellerOrderSummary toSellerSummary(SellerOrder sellerOrder, Instant now) {
        var order = sellerOrder.getOrder();
        return new SellerOrderSummary(sellerOrder.getId(), order.getOrderNumber(), sellerOrder.getStatus(),
                money(order, sellerOrder.getItemsTotal()), money(order, sellerOrder.getSellerPayout()),
                sellerOrder.getDispatchDueAt().orElse(null), sellerOrder.isDispatchOverdue(now),
                sellerOrder.getCreatedAt());
    }

    SellerOrderDetail toSellerDetail(SellerOrder sellerOrder, Optional<ShipmentView> shipment,
                                     Optional<DefectClaim> claim, Instant now) {
        var order = sellerOrder.getOrder();
        var address = order.getShippingAddress();
        return new SellerOrderDetail(sellerOrder.getId(), order.getOrderNumber(), sellerOrder.getStatus(),
                sellerOrder.getCreatedAt(), items(order, sellerOrder.getItems()),
                money(order, sellerOrder.getItemsTotal()), money(order, sellerOrder.getCommissionAmount()),
                money(order, sellerOrder.getSellerPayout()), sellerOrder.getDispatchDays(),
                sellerOrder.getDispatchDueAt().orElse(null), sellerOrder.isDispatchOverdue(now),
                provinceName(address.provinceCode()), address.district(),
                sellerOrder.getCancelledBy().orElse(null), sellerOrder.getCancelReason().orElse(null),
                sellerOrder.allows(SellerOrderTransition.CANCEL), shipment.orElse(null),
                claim.map(found -> toClaimView(order, found)).orElse(null),
                claim.flatMap(DefectClaim::getCommissionAfter).map(amount -> money(order, amount)).orElse(null),
                claim.flatMap(DefectClaim::getPayoutAfter).map(amount -> money(order, amount)).orElse(null));
    }

    AdminSellerOrderSummary toAdminSummary(SellerOrder sellerOrder) {
        var order = sellerOrder.getOrder();
        return new AdminSellerOrderSummary(sellerOrder.getId(), order.getOrderNumber(), sellerOrder.getStatus(),
                sellerOrder.getSeller().getDisplayName(), money(order, sellerOrder.refundableAmount()),
                sellerOrder.getCreatedAt());
    }

    /** Admin alıcının adını ve telefonunu görür; açık adresi görmez (add-shipments D7, K10). */
    AdminSellerOrderDetail toAdminDetail(SellerOrder sellerOrder, Optional<ShipmentView> shipment) {
        var order = sellerOrder.getOrder();
        var address = order.getShippingAddress();
        return new AdminSellerOrderDetail(sellerOrder.getId(), order.getOrderNumber(), sellerOrder.getStatus(),
                sellerOrder.getSeller().getDisplayName(), sellerOrder.getCreatedAt(),
                items(order, sellerOrder.getItems()), money(order, sellerOrder.getItemsTotal()),
                money(order, sellerOrder.getShippingFee()), money(order, sellerOrder.refundableAmount()),
                address.recipientName(), address.phone(), provinceName(address.provinceCode()), address.district(),
                shipment.orElse(null), sellerOrder.allows(SellerOrderTransition.REFUND_UNDELIVERED));
    }

    private BuyerSellerOrderView toBuyerView(Order order, SellerOrder sellerOrder, ShipmentView shipment,
                                             DefectClaim claim, Instant now) {
        return new BuyerSellerOrderView(sellerOrder.getId(), productMapper.toSellerSummary(sellerOrder.getSeller()),
                sellerOrder.getStatus(), items(order, sellerOrder.getItems()), money(order, sellerOrder.getItemsTotal()),
                money(order, sellerOrder.getShippingFee()), sellerOrder.getDispatchDays(),
                sellerOrder.getDispatchDueAt().orElse(null), sellerOrder.isDispatchOverdue(now),
                sellerOrder.getCancelledBy().orElse(null), sellerOrder.getCancelReason().orElse(null),
                sellerOrder.allows(SellerOrderTransition.CANCEL), shipment,
                claim == null ? claimableUntil(sellerOrder, shipment, now) : null,
                claim == null ? null : toClaimView(order, claim));
    }

    /**
     * Talep açılabiliyorsa son an: satıcı siparişi teslim edilmiş ve
     * teslimden bu yana pencere dolmamış olmalı (ADR-081). Hesap tek yerde;
     * servis aynı koşulu bununla denetler.
     */
    Instant claimableUntil(SellerOrder sellerOrder, ShipmentView shipment, Instant now) {
        if (sellerOrder.getStatus() != SellerOrderStatus.DELIVERED || shipment == null
                || shipment.deliveredAt() == null) {
            return null;
        }
        var until = shipment.deliveredAt().plus(properties.claims().window());
        return now.isBefore(until) ? until : null;
    }

    DefectClaimView toClaimView(Order order, DefectClaim claim) {
        return new DefectClaimView(claim.getId(), claim.getStatus(), claim.getDescription(),
                claim.getLines().stream()
                        .map(line -> new DefectClaimView.Line(line.getOrderItem().getId(),
                                line.getOrderItem().getProductName(), line.getOrderItem().getUnit(),
                                money(order, line.getOrderItem().getUnitPrice()), line.getClaimedQuantity(),
                                line.getOfferedQuantity().orElse(null), line.getRefundedQuantity().orElse(null)))
                        .toList(),
                claim.getPhotos().stream()
                        .map(photo -> new DefectClaimView.Photo(photo.getPosition(), photo.getWidth(), photo.getHeight()))
                        .toList(),
                claim.getSellerDeadlineAt(), claim.getSellerReason().orElse(null),
                claim.getBuyerDeadlineAt().orElse(null),
                claim.getRefundAmount().map(amount -> money(order, amount)).orElse(null), claim.isShippingRefunded(),
                claim.getResolvedAt().orElse(null), claim.getCreatedAt());
    }

    AdminClaimSummary toAdminClaimSummary(DefectClaim claim) {
        var sellerOrder = claim.getSellerOrder();
        var order = sellerOrder.getOrder();
        return new AdminClaimSummary(claim.getId(), order.getOrderNumber(), sellerOrder.getId(),
                sellerOrder.getSeller().getDisplayName(), claim.getStatus(), claim.getCreatedAt(),
                claim.getRefundAmount().map(amount -> money(order, amount)).orElse(null));
    }

    /** Alıcının adı, telefonu ve adresi yoktur (ADR-084). */
    AdminClaimDetail toAdminClaimDetail(DefectClaim claim) {
        var sellerOrder = claim.getSellerOrder();
        var order = sellerOrder.getOrder();
        return new AdminClaimDetail(toClaimView(order, claim), order.getOrderNumber(), sellerOrder.getId(),
                sellerOrder.getSeller().getDisplayName(), items(order, sellerOrder.getItems()),
                money(order, sellerOrder.getItemsTotal()), money(order, sellerOrder.getShippingFee()),
                claim.allows(DefectClaimTransition.ADMIN_REFUND));
    }

    ClaimSettlementView toSettlementView(Order order, OrderPricing.RefundSettlement settlement) {
        return new ClaimSettlementView(money(order, settlement.total()), settlement.shippingRefunded(),
                money(order, settlement.commissionAfter()), money(order, settlement.payoutAfter()));
    }

    private static List<OrderItemView> items(Order order, List<OrderItem> items) {
        return items.stream()
                .map(item -> new OrderItemView(item.getId(), item.getProductSlug(), item.getProductName(), item.getUnit(),
                        money(order, item.getUnitPrice()), item.getQuantity(), money(order, item.getLineTotal())))
                .toList();
    }

    private static MoneyView money(Order order, long amount) {
        return new MoneyView(amount, order.getCurrency());
    }

    private static String provinceName(String code) {
        return TurkishProvinces.nameOf(code).orElse(code);
    }
}
