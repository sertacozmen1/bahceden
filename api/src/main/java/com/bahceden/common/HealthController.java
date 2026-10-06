package com.bahceden.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Konteyner ve dağıtım hattı için sağlık ucu.
 *
 * <p>Spring Boot Actuator bilinçli olarak <strong>kullanılmıyor</strong>: ADR-002
 * bütçesinde yer almıyor ve tek ihtiyaç olan "ayakta mı?" cevabı için bir starter
 * eklemek bütçeyi gerekçesiz büyütür. Uygulama ayağa kalktıysa bu uç 200 döner.
 *
 * <p>Derinlemesine kontrol (veritabanı erişimi vb.) bilinçli olarak yok: sağlık
 * ucu, bağımlılıkları yavaşken konteyneri yeniden başlatan bir mekanizmaya
 * dönüşmemeli.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Sağlık", description = "Operasyonel sağlık kontrolü")
public class HealthController {

    @Operation(operationId = "health", summary = "Uygulamanın ayakta olup olmadığını bildirir")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public HealthResponse health() {
        return new HealthResponse("UP");
    }

    /**
     * @param status uygulama durumu; ayakta olan uygulama her zaman {@code UP} döner
     */
    @Schema(name = "HealthResponse", description = "Uygulama sağlık durumu")
    public record HealthResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Uygulama durumu", example = "UP") String status) {
    }
}
