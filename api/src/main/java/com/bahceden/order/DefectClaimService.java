package com.bahceden.order;

import com.bahceden.catalog.PhotoProcessor;
import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.storage.FileStorage;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException.FieldErrorKey;
import com.bahceden.common.web.ApiException;
import com.bahceden.common.web.PageResponse;
import com.bahceden.order.domain.DefectClaim;
import com.bahceden.order.domain.DefectClaimChange;
import com.bahceden.order.domain.DefectClaimLine;
import com.bahceden.order.domain.DefectClaimResolver;
import com.bahceden.order.domain.DefectClaimStatus;
import com.bahceden.order.domain.DefectClaimTransition;
import com.bahceden.order.domain.OrderItem;
import com.bahceden.order.domain.SellerOrderStatus;
import com.bahceden.order.dto.AdminClaimDetail;
import com.bahceden.order.dto.AdminClaimSummary;
import com.bahceden.order.dto.ClaimResponseRequest;
import com.bahceden.order.dto.ClaimSettlementView;
import com.bahceden.order.dto.DefectClaimForm;
import com.bahceden.order.dto.OrderDetail;
import com.bahceden.order.dto.SellerOrderDetail;
import com.bahceden.payment.PaymentProviderException;
import com.bahceden.payment.PaymentService;
import com.bahceden.payment.domain.RefundReason;
import com.bahceden.seller.SellerService;
import com.bahceden.shipping.ShipmentService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ayıplı ürün talebi (defect-claims spec'i; ADR-056 §4, ADR-079…082).
 *
 * <p>Talep açma tek transaction'dır: satıcı siparişi kilitlenir (aynı siparişe
 * eşzamanlı ikinci talep sırayla işlenir ve reddedilir), koşullar denetlenir,
 * fotoğraflar ilan fotoğrafı hattından geçip yazılır ve transaction geri
 * alınırsa silinir, talep ve geçmişi yazılır, satıcıya e-posta transaction
 * sonrasında gider. Açıklama ve fotoğraf anahtarı log'a yazılmaz (ADR-084).
 *
 * <p>Yanıtlar (ADR-080, ADR-082): talep satır kilidiyle okunur; satıcı kabul
 * eder, daha az adet önerir ya da gerekçeyle reddeder; alıcı teklifi veya reddi
 * kabul eder ya da yönetime taşır. İade sağlayıcıdan satıcı payından yapılır ve
 * talebin sonucuyla aynı transaction'dadır: sağlayıcı reddederse hiçbir şey
 * değişmez (502). Stok geri eklenmez.
 */
@Service
public class DefectClaimService {

    private static final Logger log = LoggerFactory.getLogger(DefectClaimService.class);

    /** Talepte en az ve en çok fotoğraf (ADR-081; ürün sahibinin kararı). */
    static final int MIN_PHOTOS = 1;
    static final int MAX_PHOTOS = 3;

    private static final String PHOTOS_FIELD = "photos";
    private static final String LINES_FIELD = "lines";
    private static final String REASON_FIELD = "reason";
    /** Ret gerekçesinin uzunluğu; satıcı kararlarındaki gerekçeyle aynı ölçüt. */
    private static final int MIN_REASON = 10;
    private static final int MAX_REASON = 500;

    /** Satıcının kararı (ADR-080). */
    public enum SellerDecision {
        ACCEPT,
        OFFER,
        REJECT
    }

    private final OrderRepository orders;
    private final SellerOrderRepository sellerOrders;
    private final DefectClaimRepository claims;
    private final DefectClaimChangeRepository changes;
    private final ShipmentService shipments;
    private final PhotoProcessor photoProcessor;
    private final FileStorage storage;
    private final OrderService orderService;
    private final OrderMapper mapper;
    private final OrderNotifier notifier;
    private final PaymentService payments;
    private final SellerService sellers;
    private final BahcedenProperties properties;
    private final Clock clock;

    DefectClaimService(OrderRepository orders, SellerOrderRepository sellerOrders, DefectClaimRepository claims,
                       DefectClaimChangeRepository changes, ShipmentService shipments, PhotoProcessor photoProcessor,
                       FileStorage storage, OrderService orderService, OrderMapper mapper, OrderNotifier notifier,
                       PaymentService payments, SellerService sellers, BahcedenProperties properties, Clock clock) {
        this.orders = orders;
        this.sellerOrders = sellerOrders;
        this.claims = claims;
        this.changes = changes;
        this.shipments = shipments;
        this.photoProcessor = photoProcessor;
        this.storage = storage;
        this.orderService = orderService;
        this.mapper = mapper;
        this.notifier = notifier;
        this.payments = payments;
        this.sellers = sellers;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Alıcının talep açması (ADR-081).
     *
     * @throws ApiException {@code ORDER_NOT_FOUND}, {@code SELLER_ORDER_NOT_FOUND}, {@code CLAIM_NOT_ALLOWED},
     *                      {@code CLAIM_WINDOW_CLOSED} veya alan bazlı {@code VALIDATION_FAILED}
     */
    @Transactional
    public OrderDetail open(long accountId, String orderNumber, long sellerOrderId, DefectClaimForm form) {
        var order = orders.findByOrderNumberAndBuyerAccountId(orderNumber, accountId)
                .orElseThrow(OrderService::orderNotFound);
        var sellerOrder = sellerOrders.findForBuyerCancel(sellerOrderId, order.getId())
                .orElseThrow(OrderService::sellerOrderNotFound);
        if (sellerOrder.getStatus() != SellerOrderStatus.DELIVERED || claims.existsForSellerOrder(sellerOrderId)) {
            throw new ApiException(ApiErrorCode.CLAIM_NOT_ALLOWED, "error.claimNotAllowed");
        }
        var now = clock.instant();
        if (mapper.claimableUntil(sellerOrder, shipments.activeView(sellerOrderId).orElse(null), now) == null) {
            throw new ApiException(ApiErrorCode.CLAIM_WINDOW_CLOSED, "error.claimWindowClosed");
        }
        var items = sellerOrder.getItems().stream().collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        requireValidLines(form.lines(), items);
        var photos = form.photos() == null ? List.<MultipartFile>of()
                : form.photos().stream().filter(photo -> !photo.isEmpty()).toList();
        if (photos.size() < MIN_PHOTOS || photos.size() > MAX_PHOTOS) {
            throw ApiException.invalidField(PHOTOS_FIELD, "validation.claimPhotos.count");
        }

        var claim = DefectClaim.open(sellerOrder, form.description().trim(),
                now.plus(properties.claims().sellerResponse()), now);
        form.lines().forEach(line -> claim.addLine(items.get(line.orderItemId()), line.quantity(), now));
        for (int position = 0; position < photos.size(); position++) {
            var processed = photoProcessor.process(bytesOf(photos.get(position)), PHOTOS_FIELD);
            claim.addPhoto(position, storage.writeWithinTransaction(processed.card()),
                    storage.writeWithinTransaction(processed.large()), processed.width(), processed.height(), now);
        }
        var saved = claims.save(claim);
        changes.save(DefectClaimChange.byAccount(saved.getId(), null, saved.getStatus(), null, accountId, now));
        notifier.claimOpened(saved, properties.claims().sellerResponse());
        log.info("Ayıplı ürün talebi açıldı: talep {}, satıcı siparişi {}, {} satır, {} fotoğraf", saved.getId(),
                sellerOrderId, form.lines().size(), photos.size());
        return orderService.detailOf(order);
    }

    /**
     * Satıcının yanıtı (ADR-080). Bütün satırlarda istenen adeti önermek
     * teklif değil kabuldür.
     *
     * @throws ApiException {@code SELLER_NOT_APPROVED}, {@code CLAIM_NOT_FOUND}, {@code CLAIM_INVALID_TRANSITION},
     *                      {@code PAYMENT_PROVIDER_ERROR} veya alan bazlı {@code VALIDATION_FAILED}
     */
    @Transactional
    public SellerOrderDetail respondAsSeller(long accountId, long sellerOrderId, SellerDecision decision,
                                             List<ClaimResponseRequest.Line> lines, String reason) {
        var seller = sellers.requireApprovedSeller(accountId);
        var claim = claims.findForSellerUpdate(sellerOrderId, seller.getId()).orElseThrow(DefectClaimService::claimNotFound);
        requireAllowed(claim, switch (decision) {
            case ACCEPT -> DefectClaimTransition.SELLER_ACCEPT;
            case OFFER -> DefectClaimTransition.SELLER_OFFER;
            case REJECT -> DefectClaimTransition.SELLER_REJECT;
        });
        var now = clock.instant();
        switch (decision) {
            case ACCEPT -> settle(claim, DefectClaimTransition.SELLER_ACCEPT, claimed(claim), DefectClaimResolver.SELLER,
                    accountId, now);
            case OFFER -> {
                var offered = quantities(claim, lines);
                if (offered.equals(claimed(claim))) {
                    settle(claim, DefectClaimTransition.SELLER_ACCEPT, offered, DefectClaimResolver.SELLER, accountId, now);
                } else {
                    var previous = claim.getStatus();
                    claim.offer(offered, now.plus(properties.claims().buyerResponse()), now);
                    changes.save(DefectClaimChange.byAccount(claim.getId(), previous, claim.getStatus(), null, accountId, now));
                    notifier.claimAnswered(claim, preview(claim, offered), properties.claims().buyerResponse());
                }
            }
            case REJECT -> {
                var text = requireReason(reason);
                var previous = claim.getStatus();
                claim.reject(text, now.plus(properties.claims().buyerResponse()), now);
                changes.save(DefectClaimChange.byAccount(claim.getId(), previous, claim.getStatus(), text, accountId, now));
                notifier.claimAnswered(claim, null, properties.claims().buyerResponse());
            }
        }
        log.info("Satıcı talebe yanıt verdi: talep {}, {}, sonuç {}", claim.getId(), decision, claim.getStatus());
        return orderService.detailForSeller(accountId, sellerOrderId);
    }

    /**
     * Bir yanıtın para etkisi; yan etkisizdir (ADR-086).
     *
     * @param lines satır başına adet; boşsa alıcının istediği adetler
     * @throws ApiException {@code SELLER_NOT_APPROVED}, {@code CLAIM_NOT_FOUND} veya alan bazlı {@code VALIDATION_FAILED}
     */
    @Transactional(readOnly = true)
    public ClaimSettlementView previewForSeller(long accountId, long sellerOrderId,
                                                List<ClaimResponseRequest.Line> lines) {
        var seller = sellers.requireApprovedSeller(accountId);
        var claim = claims.findWithDetailsForSellerOrders(List.of(sellerOrderId)).stream()
                .filter(found -> found.getSellerOrder().getSeller().getId().equals(seller.getId()))
                .findFirst()
                .orElseThrow(DefectClaimService::claimNotFound);
        var quantities = lines == null || lines.isEmpty() ? claimed(claim) : quantities(claim, lines);
        return mapper.toSettlementView(claim.getSellerOrder().getOrder(), preview(claim, quantities));
    }

    /**
     * Alıcının kabulü: teklif kabul edilirse önerilen adetler iade edilir; ret
     * kabul edilirse talep iadesiz kapanır (ADR-080).
     *
     * @throws ApiException {@code CLAIM_NOT_FOUND}, {@code CLAIM_INVALID_TRANSITION} veya {@code PAYMENT_PROVIDER_ERROR}
     */
    @Transactional
    public OrderDetail acceptAsBuyer(long accountId, String orderNumber, long sellerOrderId) {
        var claim = claims.findForBuyerUpdate(sellerOrderId, orderNumber, accountId)
                .orElseThrow(DefectClaimService::claimNotFound);
        var now = clock.instant();
        if (claim.allows(DefectClaimTransition.BUYER_ACCEPT_OFFER)) {
            settle(claim, DefectClaimTransition.BUYER_ACCEPT_OFFER, offered(claim), DefectClaimResolver.BUYER, accountId,
                    now);
        } else {
            requireAllowed(claim, DefectClaimTransition.BUYER_ACCEPT_REJECTION);
            var previous = claim.getStatus();
            claim.close(DefectClaimTransition.BUYER_ACCEPT_REJECTION, DefectClaimResolver.BUYER, now);
            changes.save(DefectClaimChange.byAccount(claim.getId(), previous, claim.getStatus(), null, accountId, now));
            notifier.claimResolved(claim, claim.getSellerReason().orElse(null));
        }
        log.info("Alıcı talebi kabul etti: talep {}, sonuç {}", claim.getId(), claim.getStatus());
        return orderService.detail(accountId, orderNumber);
    }

    /**
     * Alıcı teklifi veya reddi yönetime taşır (ADR-080).
     *
     * @throws ApiException {@code CLAIM_NOT_FOUND} veya {@code CLAIM_INVALID_TRANSITION}
     */
    @Transactional
    public OrderDetail escalateAsBuyer(long accountId, String orderNumber, long sellerOrderId) {
        var claim = claims.findForBuyerUpdate(sellerOrderId, orderNumber, accountId)
                .orElseThrow(DefectClaimService::claimNotFound);
        requireAllowed(claim, DefectClaimTransition.BUYER_ESCALATE);
        var now = clock.instant();
        var previous = claim.getStatus();
        claim.escalate(DefectClaimTransition.BUYER_ESCALATE, now);
        changes.save(DefectClaimChange.byAccount(claim.getId(), previous, claim.getStatus(), null, accountId, now));
        notifier.claimEscalated(claim, DefectClaimTransition.BUYER_ESCALATE);
        log.info("Talep yönetime taşındı: talep {}", claim.getId());
        return orderService.detail(accountId, orderNumber);
    }

    /** Admin'in kararı (ADR-080). */
    public enum AdminDecision {
        REFUND,
        REJECT
    }

    /**
     * Admin talep listesi; durumla süzülür, en yeni üstte (ADR-086).
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminClaimSummary> listForAdmin(DefectClaimStatus status, int page, int size) {
        return PageResponse.from(claims.findForAdmin(status,
                PageRequest.of(page, Math.min(size, OrderService.MAX_PAGE_SIZE))).map(mapper::toAdminClaimSummary));
    }

    /** @throws ApiException {@code CLAIM_NOT_FOUND} */
    @Transactional(readOnly = true)
    public AdminClaimDetail detailForAdmin(long claimId) {
        return mapper.toAdminClaimDetail(claims.findWithDetailsById(claimId)
                .orElseThrow(DefectClaimService::claimNotFound));
    }

    /**
     * Admin kararının para etkisi; yan etkisizdir. Adet her satırda satın
     * alınana kadar olabilir.
     *
     * @throws ApiException {@code CLAIM_NOT_FOUND} veya alan bazlı {@code VALIDATION_FAILED}
     */
    @Transactional(readOnly = true)
    public ClaimSettlementView previewForAdmin(long claimId, List<ClaimResponseRequest.Line> lines) {
        var claim = claims.findWithDetailsById(claimId).orElseThrow(DefectClaimService::claimNotFound);
        return mapper.toSettlementView(claim.getSellerOrder().getOrder(),
                preview(claim, quantities(claim, lines, purchased(claim), "validation.claimRefund.exceeds")));
    }

    /**
     * Admin yönetime taşınan talebe karar verir (ADR-080): satır başına satın
     * alınana kadar adetle iade ya da gerekçeli ret. Karar ve gerekçe geçmişe
     * admin hesabıyla yazılır; iki tarafa e-posta gider.
     *
     * @throws ApiException {@code CLAIM_NOT_FOUND}, {@code CLAIM_INVALID_TRANSITION},
     *                      {@code PAYMENT_PROVIDER_ERROR} veya alan bazlı {@code VALIDATION_FAILED}
     */
    @Transactional
    public AdminClaimDetail decideAsAdmin(long adminAccountId, long claimId, AdminDecision decision,
                                          List<ClaimResponseRequest.Line> lines, String reason) {
        var claim = claims.findByIdForUpdate(claimId).orElseThrow(DefectClaimService::claimNotFound);
        requireAllowed(claim, decision == AdminDecision.REFUND
                ? DefectClaimTransition.ADMIN_REFUND
                : DefectClaimTransition.ADMIN_REJECT);
        var now = clock.instant();
        if (decision == AdminDecision.REFUND) {
            var quantities = quantities(claim, lines, purchased(claim), "validation.claimRefund.exceeds");
            settle(claim, DefectClaimTransition.ADMIN_REFUND, quantities, DefectClaimResolver.ADMIN, adminAccountId,
                    optionalText(reason), now);
        } else {
            var text = requireReason(reason);
            var previous = claim.getStatus();
            claim.close(DefectClaimTransition.ADMIN_REJECT, DefectClaimResolver.ADMIN, now);
            changes.save(DefectClaimChange.byAccount(claimId, previous, claim.getStatus(), text, adminAccountId, now));
            notifier.claimResolved(claim, text);
        }
        log.info("Admin talebe karar verdi: talep {}, {}", claimId, decision);
        return detailForAdmin(claimId);
    }

    /**
     * Süresi dolan talebi ilerletir (ADR-083): satıcı sessizse yönetime düşer,
     * alıcı teklife sessizse teklif uygulanır, redde sessizse talep kapanır.
     * Talep kilitlenip durumu ve süresi yeniden denetlenir: bu arada bir
     * kullanıcı eylemi talebi işlediyse hiçbir şey yapılmaz. Eylemci yoktur.
     *
     * @return talep ilerlediyse yeni durumu; ilerlemediyse boş
     * @throws ApiException {@code PAYMENT_PROVIDER_ERROR}: iade yapılamadı, hiçbir şey değişmedi
     */
    @Transactional
    public Optional<DefectClaimStatus> expire(long claimId) {
        var claim = claims.findByIdForUpdate(claimId).orElse(null);
        var now = clock.instant();
        if (claim == null || !claim.isOverdue(now)) {
            return Optional.empty();
        }
        var previous = claim.getStatus();
        switch (previous) {
            case OPEN -> {
                claim.escalate(DefectClaimTransition.DEADLINE_ESCALATE, now);
                changes.save(DefectClaimChange.byDeadline(claimId, previous, claim.getStatus(), now));
                notifier.claimEscalated(claim, DefectClaimTransition.DEADLINE_ESCALATE);
            }
            case OFFERED -> settle(claim, DefectClaimTransition.DEADLINE_ACCEPT_OFFER, offered(claim),
                    DefectClaimResolver.DEADLINE, null, now);
            case REJECTED -> {
                claim.close(DefectClaimTransition.DEADLINE_CLOSE, DefectClaimResolver.DEADLINE, now);
                changes.save(DefectClaimChange.byDeadline(claimId, previous, claim.getStatus(), now));
                notifier.claimResolved(claim, claim.getSellerReason().orElse(null));
            }
            default -> throw new IllegalStateException("Süresi dolamayan talep durumu: " + previous);
        }
        log.info("Süresi dolan talep ilerledi: talep {}, {} -> {}", claimId, previous, claim.getStatus());
        return Optional.of(claim.getStatus());
    }

    /**
     * İadeyle sonuçlandırır: tutarlar hesaplanır ve talebe yazılır, geçmiş
     * kaydedilir, sağlayıcıdan satıcı payından iade edilir (ADR-082). İade
     * başarısızsa çağıranın transaction'ı geri alınır.
     *
     * @param actorAccountId kararı veren hesap; süre işinde {@code null}
     */
    private void settle(DefectClaim claim, DefectClaimTransition transition, Map<Long, Integer> quantities,
                        DefectClaimResolver by, Long actorAccountId, Instant now) {
        settle(claim, transition, quantities, by, actorAccountId, null, now);
    }

    /** @param reason geçmişe yazılan açıklama (admin kararında isteğe bağlı) */
    private void settle(DefectClaim claim, DefectClaimTransition transition, Map<Long, Integer> quantities,
                        DefectClaimResolver by, Long actorAccountId, String reason, Instant now) {
        var sellerOrder = claim.getSellerOrder();
        var settlement = preview(claim, quantities);
        var previous = claim.getStatus();
        claim.refund(transition, quantities, settlement.total(), settlement.shippingRefunded(),
                settlement.commissionAfter(), settlement.payoutAfter(), by, now);
        changes.save(actorAccountId == null
                ? DefectClaimChange.byDeadline(claim.getId(), previous, claim.getStatus(), now)
                : DefectClaimChange.byAccount(claim.getId(), previous, claim.getStatus(), reason, actorAccountId, now));
        try {
            payments.refund(sellerOrder.getOrder().getId(), sellerOrder.getId(), RefundReason.DEFECT, settlement.total(),
                    payments.subMerchantKeyOf(sellerOrder.getSeller().getId()));
        } catch (PaymentProviderException cause) {
            log.warn("Ayıplı ürün iadesi yapılamadı, karar geri alındı: talep {}", claim.getId(), cause);
            throw new ApiException(ApiErrorCode.PAYMENT_PROVIDER_ERROR, "error.paymentProvider");
        }
        notifier.claimResolved(claim, null);
    }

    /** Adetlerin para etkisi: Σ birim fiyat × adet; tam iade her satırın bütün adetleridir. */
    private static OrderPricing.RefundSettlement preview(DefectClaim claim, Map<Long, Integer> quantities) {
        var sellerOrder = claim.getSellerOrder();
        long refunded = 0;
        boolean full = true;
        for (var item : sellerOrder.getItems()) {
            int quantity = quantities.getOrDefault(item.getId(), 0);
            refunded += item.getUnitPrice() * quantity;
            full &= quantity == item.getQuantity();
        }
        return OrderPricing.afterRefund(sellerOrder.getItemsTotal(), sellerOrder.getShippingFee(), refunded,
                sellerOrder.getCommissionRateBp(), sellerOrder.getCommissionFixed(), full);
    }

    private static Map<Long, Integer> claimed(DefectClaim claim) {
        return claim.getLines().stream().collect(Collectors.toMap(line -> line.getOrderItem().getId(),
                DefectClaimLine::getClaimedQuantity));
    }

    /** Talep satırlarının satın alınan adetleri: admin bu sınıra kadar iade edebilir (ADR-080). */
    private static Map<Long, Integer> purchased(DefectClaim claim) {
        return claim.getLines().stream().collect(Collectors.toMap(line -> line.getOrderItem().getId(),
                line -> line.getOrderItem().getQuantity()));
    }

    private static String optionalText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static Map<Long, Integer> offered(DefectClaim claim) {
        return claim.getLines().stream().collect(Collectors.toMap(line -> line.getOrderItem().getId(),
                line -> line.getOfferedQuantity().orElse(0)));
    }

    /**
     * Teklif veya önizleme adetleri: her satır talepte olmalı, bir kez gelmeli,
     * istenen adeti aşmamalı; toplam en az bir adet. Yazılmayan satır 0 sayılır.
     */
    private static Map<Long, Integer> quantities(DefectClaim claim, List<ClaimResponseRequest.Line> lines) {
        return quantities(claim, lines, claimed(claim), "validation.claimOffer.exceeds");
    }

    /**
     * @param limits     sipariş satırı kimliğine göre en çok adet (satıcıda istenen, admin'de satın alınan)
     * @param exceedsKey sınırı aşan adetin mesaj anahtarı
     */
    private static Map<Long, Integer> quantities(DefectClaim claim, List<ClaimResponseRequest.Line> lines,
                                                 Map<Long, Integer> limits, String exceedsKey) {
        if (lines == null || lines.isEmpty()) {
            throw ApiException.invalidField(LINES_FIELD, "validation.claimOffer.empty");
        }
        var result = new HashMap<Long, Integer>();
        var errors = new ArrayList<FieldErrorKey>();
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            var limit = limits.get(line.orderItemId());
            if (limit == null || result.containsKey(line.orderItemId())) {
                errors.add(new FieldErrorKey("lines[" + index + "].orderItemId", "validation.claimLine.invalid"));
            } else if (line.quantity() > limit) {
                errors.add(new FieldErrorKey("lines[" + index + "].quantity", exceedsKey));
            } else {
                result.put(line.orderItemId(), line.quantity());
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "error.validation", errors);
        }
        if (result.values().stream().mapToInt(Integer::intValue).sum() == 0) {
            throw ApiException.invalidField(LINES_FIELD, "validation.claimOffer.empty");
        }
        limits.keySet().forEach(id -> result.putIfAbsent(id, 0));
        return Map.copyOf(result);
    }

    private static String requireReason(String reason) {
        var text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw ApiException.invalidField(REASON_FIELD, "validation.decisionReason.required");
        }
        if (text.length() < MIN_REASON || text.length() > MAX_REASON) {
            throw ApiException.invalidField(REASON_FIELD, "validation.decisionReason.length");
        }
        return text;
    }

    private static void requireAllowed(DefectClaim claim, DefectClaimTransition transition) {
        if (!claim.allows(transition)) {
            throw new ApiException(ApiErrorCode.CLAIM_INVALID_TRANSITION, "error.claimInvalidTransition");
        }
    }

    private static ApiException claimNotFound() {
        return new ApiException(ApiErrorCode.CLAIM_NOT_FOUND, "error.claimNotFound");
    }

    /**
     * Her satır o satıcı siparişine ait, bir kez ve satın alınan adeti aşmadan
     * gelmeli. Hatalar ilgili alana bağlanır; hepsi tek yanıtta döner.
     */
    private static void requireValidLines(List<DefectClaimForm.Line> lines, Map<Long, OrderItem> items) {
        var errors = new ArrayList<FieldErrorKey>();
        var seen = new HashSet<Long>();
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            var item = items.get(line.orderItemId());
            if (item == null || !seen.add(line.orderItemId())) {
                errors.add(new FieldErrorKey("lines[" + index + "].orderItemId", "validation.claimLine.invalid"));
            } else if (line.quantity() > item.getQuantity()) {
                errors.add(new FieldErrorKey("lines[" + index + "].quantity", "validation.claimQuantity.exceeds"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "error.validation", errors);
        }
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException cause) {
            throw new UncheckedIOException("Yüklenen fotoğraf okunamadı", cause);
        }
    }
}
