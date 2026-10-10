package com.bahceden.order;

import com.bahceden.account.AccountRepository;
import com.bahceden.account.domain.Account;
import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.i18n.Messages;
import com.bahceden.common.mail.MailSender;
import com.bahceden.common.money.MoneyFormat;
import com.bahceden.order.domain.CancelledBy;
import com.bahceden.order.domain.Order;
import com.bahceden.order.domain.OrderItem;
import com.bahceden.order.domain.SellerOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sipariş e-postaları (orders ve shipments spec'leri, add-cart-and-checkout D8,
 * D9, add-shipments D8).
 *
 * <p>E-postalar transaction <strong>başarıyla bittikten sonra</strong> ve
 * alıcının ya da satıcının hesap dilinde gönderilir (ADR-032): geri alınan bir
 * ödeme veya iptal için e-posta gitmez. Metinler transaction içinde hazırlanır,
 * yalnızca gönderim sonraya kalır. Gönderim hatası akışı kesmez, yalnızca
 * loglanır (ADR-019). Log'a adres ve sipariş içeriği yazılmaz; yalnızca sipariş
 * numarası (K10).
 */
@Component
class OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifier.class);

    private final AccountRepository accounts;
    private final MailSender mailSender;
    private final Messages messages;
    private final BahcedenProperties properties;

    OrderNotifier(AccountRepository accounts, MailSender mailSender, Messages messages, BahcedenProperties properties) {
        this.accounts = accounts;
        this.mailSender = mailSender;
        this.messages = messages;
        this.properties = properties;
    }

    /** Gönderilmeye hazır e-posta. */
    private record Mail(String to, String subject, String body, String orderNumber) {
    }

    /** Ödeme başarılı: alıcıya sipariş onayı, her satıcıya kendi satıcı siparişi. */
    void orderPaid(Order order, List<SellerOrder> sellerOrders) {
        var mails = new ArrayList<Mail>();
        accounts.findById(order.getBuyerAccountId()).ifPresent(buyer -> {
            var locale = buyer.getLocale();
            mails.add(new Mail(buyer.getEmail(),
                    messages.get("mail.orderPlaced.subject", locale, order.getOrderNumber()),
                    messages.get("mail.orderPlaced.body", locale, order.getOrderNumber(),
                            MoneyFormat.format(order.getGrandTotal(), order.getCurrency(), locale),
                            properties.webOrigin() + "/account/orders/" + order.getOrderNumber()),
                    order.getOrderNumber()));
        });
        for (var sellerOrder : sellerOrders) {
            sellerAccount(sellerOrder).ifPresent(seller -> {
                var locale = seller.getLocale();
                int quantity = sellerOrder.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
                mails.add(new Mail(seller.getEmail(),
                        messages.get("mail.sellerOrderReceived.subject", locale, order.getOrderNumber()),
                        messages.get("mail.sellerOrderReceived.body", locale, order.getOrderNumber(), quantity,
                                MoneyFormat.format(sellerOrder.getItemsTotal(), order.getCurrency(), locale),
                                sellerOrder.getDispatchDays(),
                                properties.webOrigin() + "/seller/orders/" + sellerOrder.getId()),
                        order.getOrderNumber()));
            });
        }
        sendAfterCommit(mails);
    }

    /**
     * Kargodan önce iptal: alıcı iptal ettiyse satıcıya, satıcı iptal ettiyse
     * gerekçesiyle alıcıya (ADR-056 §1).
     */
    void sellerOrderCancelled(SellerOrder sellerOrder, CancelledBy by, String reason) {
        var order = sellerOrder.getOrder();
        var mails = new ArrayList<Mail>();
        if (by == CancelledBy.BUYER) {
            sellerAccount(sellerOrder).ifPresent(seller -> {
                var locale = seller.getLocale();
                mails.add(new Mail(seller.getEmail(),
                        messages.get("mail.orderCancelledByBuyer.subject", locale, order.getOrderNumber()),
                        messages.get("mail.orderCancelledByBuyer.body", locale, order.getOrderNumber(),
                                properties.webOrigin() + "/seller/orders/" + sellerOrder.getId()),
                        order.getOrderNumber()));
            });
        } else {
            accounts.findById(order.getBuyerAccountId()).ifPresent(buyer -> {
                var locale = buyer.getLocale();
                mails.add(new Mail(buyer.getEmail(),
                        messages.get("mail.orderCancelledBySeller.subject", locale, order.getOrderNumber()),
                        messages.get("mail.orderCancelledBySeller.body", locale, order.getOrderNumber(),
                                sellerOrder.getSeller().getDisplayName(), reason,
                                MoneyFormat.format(sellerOrder.refundableAmount(), order.getCurrency(), locale),
                                properties.webOrigin() + "/account/orders/" + order.getOrderNumber()),
                        order.getOrderNumber()));
            });
        }
        sendAfterCommit(mails);
    }

    /** Kargoya verildi: alıcıya firma ve takip numarası (add-shipments D8). */
    void sellerOrderShipped(SellerOrder sellerOrder, String carrier, String trackingNumber) {
        var order = sellerOrder.getOrder();
        var mails = new ArrayList<Mail>();
        accounts.findById(order.getBuyerAccountId()).ifPresent(buyer -> {
            var locale = buyer.getLocale();
            mails.add(new Mail(buyer.getEmail(),
                    messages.get("mail.sellerOrderShipped.subject", locale, order.getOrderNumber()),
                    messages.get("mail.sellerOrderShipped.body", locale, order.getOrderNumber(),
                            sellerOrder.getSeller().getDisplayName(), carrier, trackingNumber,
                            properties.webOrigin() + "/account/orders/" + order.getOrderNumber()),
                    order.getOrderNumber()));
        });
        sendAfterCommit(mails);
    }

    /** Teslim edilemedi: alıcıya ve satıcıya; admin karar verecek (add-shipments D7, D8). */
    void sellerOrderDeliveryFailed(SellerOrder sellerOrder) {
        var order = sellerOrder.getOrder();
        var mails = new ArrayList<Mail>();
        accounts.findById(order.getBuyerAccountId()).ifPresent(buyer -> {
            var locale = buyer.getLocale();
            mails.add(new Mail(buyer.getEmail(),
                    messages.get("mail.deliveryFailedBuyer.subject", locale, order.getOrderNumber()),
                    messages.get("mail.deliveryFailedBuyer.body", locale, order.getOrderNumber(),
                            sellerOrder.getSeller().getDisplayName(),
                            properties.webOrigin() + "/account/orders/" + order.getOrderNumber()),
                    order.getOrderNumber()));
        });
        sellerAccount(sellerOrder).ifPresent(seller -> {
            var locale = seller.getLocale();
            mails.add(new Mail(seller.getEmail(),
                    messages.get("mail.deliveryFailedSeller.subject", locale, order.getOrderNumber()),
                    messages.get("mail.deliveryFailedSeller.body", locale, order.getOrderNumber(),
                            properties.webOrigin() + "/seller/orders/" + sellerOrder.getId()),
                    order.getOrderNumber()));
        });
        sendAfterCommit(mails);
    }

    /**
     * Teslim edilemeyen sipariş için admin kararı: alıcıya ve satıcıya
     * (add-shipments D7).
     *
     * @param refunded {@code true} ise iade, değilse iadesiz kapatma
     * @param reason   admin gerekçesi; iade kararında boş olabilir
     */
    void undeliveredResolved(SellerOrder sellerOrder, boolean refunded, String reason) {
        var order = sellerOrder.getOrder();
        var mails = new ArrayList<Mail>();
        var kind = refunded ? "Refunded" : "Closed";
        accounts.findById(order.getBuyerAccountId()).ifPresent(buyer -> {
            var locale = buyer.getLocale();
            mails.add(new Mail(buyer.getEmail(),
                    messages.get("mail.undelivered" + kind + ".subject", locale, order.getOrderNumber()),
                    messages.get("mail.undelivered" + kind + ".buyerBody", locale, order.getOrderNumber(),
                            sellerOrder.getSeller().getDisplayName(),
                            MoneyFormat.format(sellerOrder.refundableAmount(), order.getCurrency(), locale),
                            reasonOrDash(reason),
                            properties.webOrigin() + "/account/orders/" + order.getOrderNumber()),
                    order.getOrderNumber()));
        });
        sellerAccount(sellerOrder).ifPresent(seller -> {
            var locale = seller.getLocale();
            mails.add(new Mail(seller.getEmail(),
                    messages.get("mail.undelivered" + kind + ".subject", locale, order.getOrderNumber()),
                    messages.get("mail.undelivered" + kind + ".sellerBody", locale, order.getOrderNumber(),
                            reasonOrDash(reason), properties.webOrigin() + "/seller/orders/" + sellerOrder.getId()),
                    order.getOrderNumber()));
        });
        sendAfterCommit(mails);
    }

    private static String reasonOrDash(String reason) {
        return reason == null ? "-" : reason;
    }

    private Optional<Account> sellerAccount(SellerOrder sellerOrder) {
        return sellerOrder.getSeller().getAccountId().flatMap(accounts::findById);
    }

    private void sendAfterCommit(List<Mail> mails) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (var mail : mails) {
                    if (!mailSender.send(mail.to(), mail.subject(), mail.body())) {
                        log.warn("Sipariş e-postası gönderilemedi; işlem geçerli: sipariş {}", mail.orderNumber());
                    }
                }
            }
        });
    }
}
