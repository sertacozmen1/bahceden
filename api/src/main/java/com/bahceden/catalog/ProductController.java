package com.bahceden.catalog;

import com.bahceden.catalog.dto.ProductSummary;
import com.bahceden.common.web.ApiError;
import com.bahceden.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vitrin ürün uçları.
 *
 * <p>Controller incedir (AGENTS.md §11.2): parametreyi doğrular, service'i çağırır
 * ve DTO döner. İş kuralı burada yer almaz.
 *
 * <p>Bu uç <strong>herkese açıktır</strong> ve bu açıklık {@code SecurityConfig}
 * içinde açıkça işaretlidir: alıcı ürünleri görmek için giriş yapmak zorunda
 * değildir (storefront-catalog spec'i).
 *
 * <p>Sözleşmedeki etiket adı domain kavramını anlatır, sınıf adını değil: üretilen
 * spec'e katman detayı sızdırılmaz (AGENTS.md §10).
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Ürünler", description = "Vitrindeki ürünlerin listelenmesi")
public class ProductController {

    private final ProductService service;

    ProductController(ProductService service) {
        this.service = service;
    }

    @Operation(
            operationId = "listProducts",
            summary = "Vitrinde yayınlanmış ürünleri listeler",
            description = """
                    Yalnızca yayın durumu PUBLISHED olan ürünleri, yayın tarihine göre
                    yeniden eskiye sıralı döner. Sayfa boyutunun üst sınırı sunucuda
                    sabittir: daha büyük bir değer istenirse istek reddedilmez, değer
                    sınıra çekilir ve yanıt uygulanan boyutu bildirir.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sayfalı ürün listesi"),
            @ApiResponse(responseCode = "400", description = "Sayfalama parametresi geçersiz",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<ProductSummary> list(
            @Parameter(description = "Sayfa numarası (sıfırdan başlar)", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "{validation.page.min}") int page,
            @Parameter(description = "Sayfa boyutu; üst sınır sunucuda 50'ye çekilir", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.pageSize.min}") int size) {
        return service.listPublished(page, size);
    }
}
