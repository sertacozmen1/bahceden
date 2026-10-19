package com.bahceden.order;

import com.bahceden.common.ratelimit.RateLimitPolicy;
import com.bahceden.common.ratelimit.RateLimiter;
import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.OpenApiConfig;
import com.bahceden.common.web.PageResponse;
import com.bahceden.order.dto.CancellationRequest;
import com.bahceden.order.dto.OrderDetail;
import com.bahceden.order.dto.OrderPlaced;
import com.bahceden.order.dto.OrderSummary;
import com.bahceden.order.dto.PlaceOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alıcının siparişleri (cart-and-checkout ve orders spec'leri,
 * add-cart-and-checkout D6, D8). Sipariş token'daki hesaba aittir; başkasının
 * siparişi 404 döner.
 */
@RestController
@Validated
@RequestMapping("/api/v1/orders")
@Tag(name = "Siparişler", description = "Sepetten sipariş, ödeme ve alıcının siparişleri")
public class OrderController {

    private static final String SELF_PATH = "/api/v1/orders/";

    private final CheckoutService checkout;
    private final OrderService service;
    private final OrderCancellationService cancellation;
    private final RateLimiter rateLimiter;

    OrderController(CheckoutService checkout, OrderService service, OrderCancellationService cancellation,
                    RateLimiter rateLimiter) {
        this.checkout = checkout;
        this.service = service;
        this.cancellation = cancellation;
        this.rateLimiter = rateLimiter;
    }

    @Operation(operationId = "placeOrder", summary = "Sepetten sipariş oluşturur ve ödemeyi başlatır",
            description = """
                    Sepet satıcılara göre bölünür; fiyat, kargo ücreti ve komisyon bu anda sabitlenir. Stok düşmez.
                    Yanıttaki ödeme sayfasına gidilir. Idempotency-Key başlığı zorunludur: aynı anahtarla tekrarlanan
                    istek yeni sipariş açmaz, ilk siparişi döner.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "201", description = "Sipariş oluştu; Location başlığı siparişin adresi")
    @ApiResponse(responseCode = "400", description = "Alan veya Idempotency-Key geçersiz (VALIDATION_FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Adres yok veya başkasına ait (ADDRESS_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = """
            E-posta doğrulanmamış (ORDER_EMAIL_NOT_VERIFIED), sepet boş (CART_EMPTY), satın alınamayan veya stoğu
            aşan satır var (CART_HAS_UNAVAILABLE_ITEMS) ya da adrese teslimat yok (ADDRESS_NOT_DELIVERABLE)""",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Çok fazla sipariş denemesi (RATE_LIMITED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Ödeme sağlayıcısına ulaşılamadı (PAYMENT_PROVIDER_ERROR)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderPlaced> place(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @Parameter(description = "Tekrar anahtarı: istemcinin ürettiği 8–64 karakter (harf, rakam, tire, alt çizgi)",
                    example = "c2f1a7e0-5b3d-4c1e-9f2a-7d6b8e9c0a1b", required = true)
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        var accountId = accountId(token);
        rateLimiter.check(RateLimitPolicy.ORDER_CREATE, Long.toString(accountId));
        var placed = checkout.placeOrder(accountId, idempotencyKey, request.addressId());
        return ResponseEntity.created(URI.create(SELF_PATH + placed.orderNumber())).body(placed);
    }

    @Operation(operationId = "completeOrderPayment", summary = "Ödemeyi doğrular ve siparişi sonuçlandırır",
            description = """
                    Sonuç ödeme sağlayıcısından okunur. Başarılıysa stok düşer ve satın alınan ürünler sepetten
                    çıkar; stok yetmezse ödemenin tamamı iade edilir. Süre dolduysa sipariş düşer. Tekrar
                    çağrılabilir; sonuçlanmış siparişte mevcut durum döner.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Siparişin güncel hali")
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Sipariş yok veya başkasına ait (ORDER_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Ödeme sağlayıcısına ulaşılamadı (PAYMENT_PROVIDER_ERROR)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{orderNumber}/payment", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderDetail completePayment(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                                       @PathVariable String orderNumber) {
        return checkout.completePayment(accountId(token), orderNumber);
    }

    @Operation(operationId = "listOwnOrders", summary = "Alıcının siparişlerini listeler",
            description = "En yeni üstte. Sayfa boyutunun üst sınırı sunucuda 50'dir.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Siparişler")
    @ApiResponse(responseCode = "400", description = "Sayfalama değeri geçersiz (VALIDATION_FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<OrderSummary> list(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @Parameter(description = "Sayfa numarası (sıfırdan başlar)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "{validation.page.min}") int page,
            @Parameter(description = "Sayfa boyutu; üst sınır sunucuda 50'ye çekilir", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.pageSize.min}") int size) {
        return service.listOwn(accountId(token), page, size);
    }

    @Operation(operationId = "getOwnOrder", summary = "Alıcının sipariş detayını döner",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Sipariş")
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Sipariş yok veya başkasına ait (ORDER_NOT_FOUND)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @GetMapping(path = "/{orderNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderDetail get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @PathVariable String orderNumber) {
        return service.detail(accountId(token), orderNumber);
    }

    @Operation(operationId = "cancelSellerOrderAsBuyer", summary = "Kargoya verilmemiş satıcı siparişini iptal eder",
            description = """
                    Alıcı, kargoya verilmemiş satıcı siparişini her zaman iptal edebilir; gerekçe isteğe bağlıdır.
                    Ürün tutarı ve kargo ücreti iade edilir, stok geri eklenir, satıcıya e-posta gider. Diğer satıcı
                    siparişleri etkilenmez.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponse(responseCode = "200", description = "Siparişin güncel hali")
    @ApiResponse(responseCode = "400", description = "Gerekçe çok uzun (VALIDATION_FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Oturum yok (AUTH_REQUIRED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404",
            description = "Sipariş (ORDER_NOT_FOUND) veya satıcı siparişi (SELLER_ORDER_NOT_FOUND) yok ya da başkasına ait",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Satıcı siparişi iptal edilebilir durumda değil: iptal edilmiş veya kargoya verilmiş (ORDER_INVALID_TRANSITION)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Gönderi iptal edilemedi (SHIPPING_INTEGRATOR_ERROR) veya iade yapılamadı (PAYMENT_PROVIDER_ERROR); hiçbir şey değişmedi",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    @PostMapping(path = "/{orderNumber}/seller-orders/{sellerOrderId}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderDetail cancel(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                              @PathVariable String orderNumber, @PathVariable long sellerOrderId,
                              @Valid @RequestBody CancellationRequest request) {
        return cancellation.cancelByBuyer(accountId(token), orderNumber, sellerOrderId, request.reason());
    }

    private static long accountId(Jwt token) {
        return Long.parseLong(token.getSubject());
    }
}
