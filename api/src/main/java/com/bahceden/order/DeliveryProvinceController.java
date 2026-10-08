package com.bahceden.order;

import com.bahceden.common.web.PageResponse;
import com.bahceden.order.dto.DeliveryProvinceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teslimat yapılan iller (add-cart-and-checkout D5). Herkese açıktır: adres
 * formu bunu ilk açılışta, oturumdan bağımsız okur.
 */
@RestController
@Tag(name = "Teslimat", description = "Teslimat yapılan iller")
public class DeliveryProvinceController {

    private final AddressService service;

    DeliveryProvinceController(AddressService service) {
        this.service = service;
    }

    @Operation(operationId = "listDeliveryProvinces", summary = "Teslimat yapılan illeri listeler",
            description = "Liste yapılandırmadadır; tamamı tek sayfada döner.")
    @ApiResponse(responseCode = "200", description = "Teslimat yapılan iller")
    @GetMapping(path = "/api/v1/delivery/provinces", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<DeliveryProvinceView> list() {
        return service.deliveryProvinces();
    }
}
