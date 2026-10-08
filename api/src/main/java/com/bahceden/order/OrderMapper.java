package com.bahceden.order;

import com.bahceden.catalog.ProductMapper;
import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.common.geo.TurkishProvinces;
import com.bahceden.order.domain.Order;
import com.bahceden.order.domain.OrderItem;
import com.bahceden.order.domain.SellerOrder;
import com.bahceden.order.domain.SellerOrderTransition;
import com.bahceden.order.dto.BuyerSellerOrderView;
import com.bahceden.order.dto.OrderDetail;
import com.bahceden.order.dto.OrderItemView;
import com.bahceden.order.dto.OrderSummary;
import com.bahceden.order.dto.SellerOrderDetail;
import com.bahceden.order.dto.SellerOrderSummary;
import com.bahceden.order.dto.ShippingAddressView;
import java.time.Instant;
import java.util.List;
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

    OrderMapper(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    OrderSummary toSummary(Order order) {
        return new OrderSummary(order.getOrderNumber(), order.getStatus(), money(order, order.getGrandTotal()),
                order.getCreatedAt());
    }

    OrderDetail toDetail(Order order, List<SellerOrder> sellerOrders, Instant now) {
        var address = order.getShippingAddress();
        var shipping = new ShippingAddressView(address.recipientName(), address.phone(), address.provinceCode(),
                provinceName(address.provinceCode()), address.district(), address.addressLine(), address.postalCode());
        return new OrderDetail(order.getOrderNumber(), order.getStatus(), order.getCreatedAt(),
                order.getPaidAt().orElse(null), shipping, money(order, order.getItemsTotal()),
                money(order, order.getShippingTotal()), money(order, order.getGrandTotal()),
                sellerOrders.stream().map(sellerOrder -> toBuyerView(order, sellerOrder, now)).toList());
    }

    SellerOrderSummary toSellerSummary(SellerOrder sellerOrder, Instant now) {
        var order = sellerOrder.getOrder();
        return new SellerOrderSummary(sellerOrder.getId(), order.getOrderNumber(), sellerOrder.getStatus(),
                money(order, sellerOrder.getItemsTotal()), money(order, sellerOrder.getSellerPayout()),
                sellerOrder.getDispatchDueAt().orElse(null), sellerOrder.isDispatchOverdue(now),
                sellerOrder.getCreatedAt());
    }

    SellerOrderDetail toSellerDetail(SellerOrder sellerOrder, Instant now) {
        var order = sellerOrder.getOrder();
        var address = order.getShippingAddress();
        return new SellerOrderDetail(sellerOrder.getId(), order.getOrderNumber(), sellerOrder.getStatus(),
                sellerOrder.getCreatedAt(), items(order, sellerOrder.getItems()),
                money(order, sellerOrder.getItemsTotal()), money(order, sellerOrder.getCommissionAmount()),
                money(order, sellerOrder.getSellerPayout()), sellerOrder.getDispatchDays(),
                sellerOrder.getDispatchDueAt().orElse(null), sellerOrder.isDispatchOverdue(now),
                provinceName(address.provinceCode()), address.district(),
                sellerOrder.getCancelledBy().orElse(null), sellerOrder.getCancelReason().orElse(null),
                sellerOrder.allows(SellerOrderTransition.CANCEL));
    }

    private BuyerSellerOrderView toBuyerView(Order order, SellerOrder sellerOrder, Instant now) {
        return new BuyerSellerOrderView(sellerOrder.getId(), productMapper.toSellerSummary(sellerOrder.getSeller()),
                sellerOrder.getStatus(), items(order, sellerOrder.getItems()), money(order, sellerOrder.getItemsTotal()),
                money(order, sellerOrder.getShippingFee()), sellerOrder.getDispatchDays(),
                sellerOrder.getDispatchDueAt().orElse(null), sellerOrder.isDispatchOverdue(now),
                sellerOrder.getCancelledBy().orElse(null), sellerOrder.getCancelReason().orElse(null),
                sellerOrder.allows(SellerOrderTransition.CANCEL));
    }

    private static List<OrderItemView> items(Order order, List<OrderItem> items) {
        return items.stream()
                .map(item -> new OrderItemView(item.getProductSlug(), item.getProductName(), item.getUnit(),
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
