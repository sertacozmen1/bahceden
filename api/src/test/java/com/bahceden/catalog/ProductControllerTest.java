package com.bahceden.catalog;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bahceden.catalog.domain.ProductUnit;
import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.catalog.dto.ProductSummary;
import com.bahceden.catalog.dto.SellerSummary;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.GlobalExceptionHandler;
import com.bahceden.common.web.PageResponse;
import com.bahceden.common.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vitrin ucunun HTTP sözleşmesi (storefront-catalog spec'i, AGENTS.md §11.5).
 *
 * <p>Sözleşme davranışı {@code critical}: alan adları ve sayfalama şekli
 * istemcinin doğrudan dayandığı yüzeydir, sessizce değişemez (ADR-005).
 *
 * <p>Veritabanına çıkmaz: service taklit edilir, böylece test hızlı kalır ve
 * yalnızca web katmanını doğrular. Sorgu davranışı {@code ProductRepositoryTest}
 * içinde gerçek PostgreSQL'e karşı doğrulanır.
 */
@Tag("critical")
@WebMvcTest(ProductController.class)
// Gerçek SecurityConfig yüklenir: ucun kimlik doğrulaması olmadan erişilebildiği
// (permitAll) de bu testin doğruladığı sözleşmenin parçasıdır.
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "bahceden.web-origin=http://localhost:3000")
@DisplayName("GET /api/v1/products sözleşmesi")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService service;

    @Test
    @DisplayName("Yanıt alan adları ve sayfalama şekli sözleşmeye uygundur")
    void yanit_sozlesmeye_uygundur() throws Exception {
        when(service.listPublished(anyInt(), anyInt())).thenReturn(ornekSayfa());

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.items[0].slug").value("sultani-uzum"))
                .andExpect(jsonPath("$.items[0].name").value("Sultani Üzüm"))
                .andExpect(jsonPath("$.items[0].unit").value("KILOGRAM"))
                .andExpect(jsonPath("$.items[0].stockQuantity").value(120))
                // Para minor unit tamsayı + açık para birimi (ADR-006).
                .andExpect(jsonPath("$.items[0].unitPrice.amount").value(4250))
                .andExpect(jsonPath("$.items[0].unitPrice.currency").value("TRY"))
                .andExpect(jsonPath("$.items[0].seller.displayName").value("Örnek Üretici (Manisa)"))
                .andExpect(jsonPath("$.items[0].seller.city").value("Manisa"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("Sayfalama parametresi verilmezse varsayılanlar uygulanır")
    void varsayilan_sayfalama() throws Exception {
        when(service.listPublished(anyInt(), anyInt())).thenReturn(bosSayfa(0, 20));

        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());

        verify(service).listPublished(0, 20);
    }

    @Test
    @DisplayName("Üst sınırın üzerindeki sayfa boyutu reddedilmez, service'e iletilir ve sunucuda sınıra çekilir")
    void ust_sinir_ustu_sayfa_boyutu() throws Exception {
        when(service.listPublished(anyInt(), anyInt())).thenReturn(bosSayfa(0, ProductService.MAX_PAGE_SIZE));

        mockMvc.perform(get("/api/v1/products").param("size", "500"))
                .andExpect(status().isOk())
                // Yanıt UYGULANAN boyutu bildirir, istenen boyutu değil.
                .andExpect(jsonPath("$.size").value(ProductService.MAX_PAGE_SIZE));

        var sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(service).listPublished(anyInt(), sizeCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(sizeCaptor.getValue()).isEqualTo(500);
    }

    @Test
    @DisplayName("Boş katalog boş liste döner, hata üretmez")
    void bos_katalog() throws Exception {
        when(service.listPublished(anyInt(), anyInt())).thenReturn(bosSayfa(0, 20));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Negatif sayfa numarası ortak hata gövdesiyle reddedilir")
    void negatif_sayfa_reddedilir() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value("Gönderilen değerler geçersiz."))
                .andExpect(jsonPath("$.fieldErrors", org.hamcrest.Matchers.hasSize(1)))
                // Alan adı parametrenin kendisidir: controller metot adı ("list")
                // sözleşmeye sızmaz (AGENTS.md §10).
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Sayfa numarası negatif olamaz"));
    }

    @Test
    @DisplayName("Sayı olmayan sayfa boyutu ortak hata gövdesiyle reddedilir")
    void sayi_olmayan_boyut_reddedilir() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "cok"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
    }

    private static PageResponse<ProductSummary> ornekSayfa() {
        var product = new ProductSummary(
                "sultani-uzum",
                "Sultani Üzüm",
                ProductUnit.KILOGRAM,
                new MoneyView(4250, "TRY"),
                120,
                new SellerSummary("ornek-uretici-manisa", "Örnek Üretici (Manisa)", "Manisa"));
        return new PageResponse<>(List.of(product), 0, 20, 1, 1);
    }

    private static PageResponse<ProductSummary> bosSayfa(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0);
    }
}
