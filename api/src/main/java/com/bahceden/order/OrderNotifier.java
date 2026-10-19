package com.bahceden.order;

import com.bahceden.account.AccountRepository;
import com.bahceden.account.domain.Account;
import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.i18n.Messages;
import com.bahceden.common.mail.MailSender;
import com.bahceden.common.money.MoneyFormat;
import com.bahceden.common.time.DateTimeFormat;
import com.bahceden.order.domain.CancelledBy;
import com.bahceden.order.domain.DefectClaim;
import com.bahceden.order.domain.Order;
import com.bahceden.order.domain.OrderItem;
import com.bahceden.order.domain.SellerOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

    /**
     * Teslim edildi: alıcıya; sorun bildirimi için son an Türkiye saatiyle ve
     * süre saat olarak (add-delivered-email D1, D2). Son an sipariş detayındaki
     * {@code claimableUntil} ile aynı toplamdır: teslim anı + talep süresi.
     */
    void sellerOrderDelivered(SellerOrder sellerOrder, Instant deliveredAt) {
        var order = sellerOrder.getOrder();
        var window = properties.claims().window();
        var mails = new ArrayList<Mail>();
        accounts.findById(order.getBuyerAccountId()).ifPresent(buyer -> {
            var locale = buyer.getLocale();
            mails.add(new Mail(buyer.getEmail(),
                    messages.get("mail.sellerOrderDelivered.subject", locale, order.getOrderNumber()),
                    messages.get("mail.sellerOrderDelivered.body", locale, order.getOrderNumber(),
                            sellerOrder.getSeller().getDisplayName(),
                            DateTimeFormat.format(deliveredAt.plus(window), locale), window.toHours(),
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

    /**
     * Ayıplı ürün talebi açıldı: satıcıya satırlar, adetler ve yanıt süresi
     * (ADR-085). Alıcının açıklaması e-postaya girmez; satıcı paneldeki
     * talepte görür.
     *
     * @param responseTime satıcının yanıt süresi
     */
    void claimOpened(DefectClaim claim, Duration responseTime) {
        var sellerOrder = claim.getSellerOrder();
        var number = sellerOrder.getOrder().getOrderNumber();
        var mails = new ArrayList<Mail>();
        sellerAccount(sellerOrder).ifPresent(seller -> {
            var locale = seller.getLocale();
            var lines = claim.getLines().stream()
                    .map(line -> messages.get("mail.claimOpened.line", locale, line.getOrderItem().getProductName(),
                            line.getClaimedQuantity()))
                    .collect(Collectors.joining("\n"));
            mails.add(new Mail(seller.getEmail(),
                    messages.get("mail.claimOpened.subject", locale, number),
                    messages.get("mail.claimOpened.body", locale, number, lines, responseTime.toHours(),
                            properties.webOrigin() + "/seller/orders/" + sellerOrder.getId()),
                    number));
        });
        sendAfterCommit(mails);
    }

    /**
     * Satıcı teklif verdi ya da reddetti: alıcıya teklif edilen tutar veya
     * gerekçe ve yanıt süresi; sessiz kalırsa ne olacağı yazılır (ADR-083, ADR-085).
     *
     * @param offer kısmi teklifin para etkisi; retde {@code null}
     */
    void claimAnswered(DefectClaim claim, OrderPricing.RefundSettlement offer, Duration responseTime) {
        var sellerOrder = claim.getSellerOrder();
        var order = sellerOrder.getOrder();
        var number = order.getOrderNumber();
        var link = properties.webOrigin() + "/account/orders/" + number;
        var mails = new ArrayList<Mail>();
        accounts.findById(order.getBuyerAccountId()).ifPresent(buyer -> {
            var locale = buyer.getLocale();
            var seller = sellerOrder.getSeller().getDisplayName();
            mails.add(offer == null
                    ? new Mail(buyer.getEmail(), messages.get("mail.claimRejected.subject", locale, number),
                            messages.get("mail.claimRejected.body", locale, number, seller,
                                    claim.getSellerReason().orElse(""), responseTime.toHours(), link), number)
                    : new Mail(buyer.getEmail(), messages.get("mail.claimOffered.subject", locale, number),
                            messages.get("mail.claimOffered.body", locale, number, seller,
                                    MoneyFormat.format(offer.total(), order.getCurrency(), locale),
                                    responseTime.toHours(), link), number));
        });
        sendAfterCommit(mails);
    }

    /** Talep yönetime taşındı: iki tarafa (ADR-085). */
    void claimEscalated(DefectClaim claim) {
        var sellerOrder = claim.getSellerOrder();
        var number = sellerOrder.getOrder().getOrderNumber();
        var mails = new ArrayList<Mail>();
        accounts.findById(sellerOrder.getOrder().getBuyerAccountId()).ifPresent(buyer -> mails.add(new Mail(
                buyer.getEmail(), messages.get("mail.claimEscalated.subject", buyer.getLocale(), number),
                messages.get("mail.claimEscalated.buyerBody", buyer.getLocale(), number,
                        properties.webOrigin() + "/account/orders/" + number), number)));
        sellerAccount(sellerOrder).ifPresent(seller -> mails.add(new Mail(
                seller.getEmail(), messages.get("mail.claimEscalated.subject", seller.getLocale(), number),
                messages.get("mail.claimEscalated.sellerBody", seller.getLocale(), number,
                        properties.webOrigin() + "/seller/orders/" + sellerOrder.getId()), number)));
        sendAfterCommit(mails);
    }

    /**
     * Talep sonuçlandı: iadede alıcıya tutar, satıcıya tutar ve yeni hakediş;
     * kapanışta iki tarafa gerekçe (ADR-085).
     *
     * @param closingReason kapanışın gerekçesi (satıcının reddi ya da admin'in kararı); iadede kullanılmaz
     */
    void claimResolved(DefectClaim claim, String closingReason) {
        var sellerOrder = claim.getSellerOrder();
        var order = sellerOrder.getOrder();
        var number = order.getOrderNumber();
        var refunded = claim.getRefundAmount();
        var mails = new ArrayList<Mail>();
        accounts.findById(order.getBuyerAccountId()).ifPresent(buyer -> {
            var locale = buyer.getLocale();
            var link = properties.webOrigin() + "/account/orders/" + number;
            mails.add(refunded.isPresent()
                    ? new Mail(buyer.getEmail(), messages.get("mail.claimRefunded.subject", locale, number),
                            messages.get("mail.claimRefunded.buyerBody", locale, number,
                                    MoneyFormat.format(refunded.get(), order.getCurrency(), locale), link), number)
                    : new Mail(buyer.getEmail(), messages.get("mail.claimClosed.subject", locale, number),
                            messages.get("mail.claimClosed.buyerBody", locale, number, closingReason, link), number));
        });
        sellerAccount(sellerOrder).ifPresent(seller -> {
            var locale = seller.getLocale();
            var link = properties.webOrigin() + "/seller/orders/" + sellerOrder.getId();
            mails.add(refunded.isPresent()
                    ? new Mail(seller.getEmail(), messages.get("mail.claimRefunded.subject", locale, number),
                            messages.get("mail.claimRefunded.sellerBody", locale, number,
                                    MoneyFormat.format(refunded.get(), order.getCurrency(), locale),
                                    MoneyFormat.format(claim.getPayoutAfter().orElse(0L), order.getCurrency(), locale),
                                    link), number)
                    : new Mail(seller.getEmail(), messages.get("mail.claimClosed.subject", locale, number),
                            messages.get("mail.claimClosed.sellerBody", locale, number, closingReason, link), number));
        });
        sendAfterCommit(mails);
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
