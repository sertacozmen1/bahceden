package com.bahceden.shipping;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.geo.TurkishProvinces;
import com.bahceden.common.storage.FileStorage;
import com.bahceden.common.storage.FileType;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.order.SellerOrderRepository;
import com.bahceden.order.domain.SellerOrder;
import com.bahceden.order.domain.SellerOrderStatus;
import com.bahceden.seller.SellerService;
import com.bahceden.shipping.ShippingIntegrator.Recipient;
import com.bahceden.shipping.ShippingIntegrator.Sender;
import com.bahceden.shipping.ShippingIntegrator.ShipmentRequest;
import com.bahceden.shipping.domain.PackageSize;
import com.bahceden.shipping.domain.Shipment;
import com.bahceden.shipping.dto.ShipmentView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gönderi açma, etiket ve gönderi okuma (shipments spec'i, ADR-057,
 * add-shipments D3, D4, D6).
 *
 * <p>Satıcı önce onaylı satıcı kapısından (ADR-044), sonra sorgu seviyesindeki
 * sahiplik denetiminden geçer (ADR-007); başkasının siparişi 404 döner. Gönderi
 * açılırken satıcı siparişi kilitlenir: aynı anda basılan iki "kargoya hazırla"
 * tek gönderi açar. Gönderi açılması satıcı siparişinin durumunu değiştirmez
 * (D2). Log'a takip numarası ve adres yazılmaz; yalnızca kimlikler (K10).
 */
@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    /** Etiket için kabul edilen türler (D4); başka tür entegratör hatası sayılır. */
    private static final Set<FileType> LABEL_TYPES = EnumSet.of(FileType.PDF, FileType.PNG);

    /** Ödemesi tamamlanmamış satıcı siparişi satıcıya görünmez (orders spec'i). */
    private static final Set<SellerOrderStatus> HIDDEN_FROM_SELLER =
            EnumSet.of(SellerOrderStatus.PENDING_PAYMENT, SellerOrderStatus.VOID);

    private static final String REFERENCE_PREFIX = "BHC-";

    private final ShipmentRepository shipments;
    private final SellerOrderRepository sellerOrders;
    private final SellerService sellers;
    private final ShippingIntegrator integrator;
    private final FileStorage storage;
    private final BahcedenProperties properties;
    private final Clock clock;

    ShipmentService(ShipmentRepository shipments, SellerOrderRepository sellerOrders, SellerService sellers,
                    ShippingIntegrator integrator, FileStorage storage, BahcedenProperties properties, Clock clock) {
        this.shipments = shipments;
        this.sellerOrders = sellerOrders;
        this.sellers = sellers;
        this.integrator = integrator;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    /** Açılan veya zaten açık olan gönderi. */
    public record Opened(ShipmentView shipment, boolean created) {
    }

    /** Sunulmak üzere okunmuş etiket. */
    public record Label(FileType type, byte[] content) {
    }

    /**
     * Gönderi açar; etkin gönderi varsa onu döner.
     *
     * @throws ApiException {@code SELLER_NOT_APPROVED}, {@code SELLER_ORDER_NOT_FOUND},
     *                      {@code ORDER_INVALID_TRANSITION}, {@code SHIPMENT_SENDER_PHONE_MISSING}
     *                      veya {@code SHIPPING_INTEGRATOR_ERROR}
     */
    @Transactional
    public Opened open(long accountId, long sellerOrderId, PackageSize packageSize) {
        var seller = sellers.requireApprovedSeller(accountId);
        var sellerOrder = sellerOrders.findForSellerUpdate(sellerOrderId, seller.getId())
                .filter(found -> !HIDDEN_FROM_SELLER.contains(found.getStatus()))
                .orElseThrow(ShipmentService::sellerOrderNotFound);
        var existing = shipments.findActive(sellerOrderId);
        if (existing.isPresent()) {
            return new Opened(view(existing.get()), false);
        }
        if (sellerOrder.getStatus() != SellerOrderStatus.NEW) {
            throw new ApiException(ApiErrorCode.ORDER_INVALID_TRANSITION, "error.shipmentNotAllowed");
        }
        var phone = seller.getPhone()
                .orElseThrow(() -> new ApiException(ApiErrorCode.SHIPMENT_SENDER_PHONE_MISSING,
                        "error.shipmentSenderPhoneMissing"));
        var reference = REFERENCE_PREFIX + sellerOrderId + "-" + (shipments.countBySellerOrderId(sellerOrderId) + 1);
        var request = new ShipmentRequest(reference, new Sender(seller.getDisplayName(), seller.getCity(), phone),
                recipient(sellerOrder), desi(packageSize));
        try {
            var created = integrator.create(request);
            var label = integrator.label(created.integratorRef());
            var type = FileType.detect(label).filter(LABEL_TYPES::contains)
                    .orElseThrow(() -> new ShippingIntegratorException("Etiket türü desteklenmiyor"));
            var labelKey = storage.write(label);
            var shipment = shipments.save(Shipment.created(sellerOrderId, properties.shipping().integrator(),
                    created.integratorRef(), created.carrier(), created.trackingNumber(), packageSize, labelKey,
                    type.contentType(), clock.instant()));
            log.info("Gönderi açıldı: satıcı siparişi {}, gönderi {}", sellerOrderId, shipment.getId());
            return new Opened(view(shipment), true);
        } catch (ShippingIntegratorException cause) {
            log.warn("Gönderi açılamadı: satıcı siparişi {}", sellerOrderId, cause);
            throw integratorError();
        }
    }

    /**
     * Satıcının kendi gönderisinin etiketi.
     *
     * @throws ApiException {@code SELLER_NOT_APPROVED} veya {@code SELLER_ORDER_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public Label label(long accountId, long sellerOrderId) {
        var seller = sellers.requireApprovedSeller(accountId);
        var shipment = shipments.findActiveForSeller(sellerOrderId, seller.getId())
                .orElseThrow(ShipmentService::sellerOrderNotFound);
        var type = FileType.ofContentType(shipment.getLabelContentType())
                .orElseThrow(() -> new IllegalStateException("Kayıtlı etiket türü tanınmıyor: gönderi " + shipment.getId()));
        try (var content = storage.open(shipment.getLabelKey())) {
            return new Label(type, content.readAllBytes());
        } catch (IOException cause) {
            throw new UncheckedIOException("Etiket okunamadı: gönderi " + shipment.getId(), cause);
        }
    }

    @Transactional(readOnly = true)
    public Optional<ShipmentView> activeView(long sellerOrderId) {
        return shipments.findActive(sellerOrderId).map(ShipmentService::view);
    }

    /** Birden çok satıcı siparişinin etkin gönderileri tek sorguda. */
    @Transactional(readOnly = true)
    public Map<Long, ShipmentView> activeViews(Collection<Long> sellerOrderIds) {
        if (sellerOrderIds.isEmpty()) {
            return Map.of();
        }
        return shipments.findActiveFor(sellerOrderIds).stream()
                .collect(Collectors.toMap(Shipment::getSellerOrderId, ShipmentService::view, (first, second) -> first));
    }

    /**
     * İptal edilen satıcı siparişinin etkin gönderisini entegratörde iptal eder
     * (D6). Çağıranın transaction'ında çalışır: entegratör reddederse
     * {@link ShippingIntegratorException} çağırana geçer ve iptal geri alınır.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelActive(long sellerOrderId) {
        shipments.findActiveForUpdate(sellerOrderId).ifPresent(shipment -> {
            integrator.cancel(shipment.getIntegratorRef());
            shipment.cancel(clock.instant());
            log.info("Gönderi iptal edildi: satıcı siparişi {}, gönderi {}", sellerOrderId, shipment.getId());
        });
    }

    static ShipmentView view(Shipment shipment) {
        return new ShipmentView(shipment.getCarrier(), shipment.getTrackingNumber(), shipment.getStatus(),
                shipment.getPackageSize(), shipment.getCreatedAt(), shipment.getHandedOverAt().orElse(null),
                shipment.getDeliveredAt().orElse(null));
    }

    static ApiException integratorError() {
        return new ApiException(ApiErrorCode.SHIPPING_INTEGRATOR_ERROR, "error.shippingIntegrator");
    }

    private Recipient recipient(SellerOrder sellerOrder) {
        var address = sellerOrder.getOrder().getShippingAddress();
        return new Recipient(address.recipientName(), address.phone(),
                TurkishProvinces.nameOf(address.provinceCode()).orElse(address.provinceCode()), address.district(),
                address.addressLine(), address.postalCode());
    }

    private int desi(PackageSize size) {
        var desi = properties.shipping().packageDesi();
        return switch (size) {
            case SMALL -> desi.small();
            case MEDIUM -> desi.medium();
            case LARGE -> desi.large();
        };
    }

    private static ApiException sellerOrderNotFound() {
        return new ApiException(ApiErrorCode.SELLER_ORDER_NOT_FOUND, "error.sellerOrderNotFound");
    }
}
