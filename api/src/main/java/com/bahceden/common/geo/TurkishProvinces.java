package com.bahceden.common.geo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Türkiye'nin 81 ili, plaka koduyla (add-cart-and-checkout D5).
 *
 * <p>Sabit bir katalogdur: teslimat yapılan iller yapılandırmadadır
 * ({@code bahceden.commerce.delivery-provinces}) ve açılışta her kodun burada
 * olduğu doğrulanır; yazım hatası çalışma anında değil açılışta görünür. İl adı
 * özel isimdir ve çevrilmez (AGENTS.md §12.4).
 */
public final class TurkishProvinces {

    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        String[] names = {
                "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Amasya", "Ankara", "Antalya", "Artvin", "Aydın",
                "Balıkesir", "Bilecik", "Bingöl", "Bitlis", "Bolu", "Burdur", "Bursa", "Çanakkale", "Çankırı",
                "Çorum", "Denizli", "Diyarbakır", "Edirne", "Elazığ", "Erzincan", "Erzurum", "Eskişehir",
                "Gaziantep", "Giresun", "Gümüşhane", "Hakkari", "Hatay", "Isparta", "Mersin", "İstanbul", "İzmir",
                "Kars", "Kastamonu", "Kayseri", "Kırklareli", "Kırşehir", "Kocaeli", "Konya", "Kütahya", "Malatya",
                "Manisa", "Kahramanmaraş", "Mardin", "Muğla", "Muş", "Nevşehir", "Niğde", "Ordu", "Rize", "Sakarya",
                "Samsun", "Siirt", "Sinop", "Sivas", "Tekirdağ", "Tokat", "Trabzon", "Tunceli", "Şanlıurfa", "Uşak",
                "Van", "Yozgat", "Zonguldak", "Aksaray", "Bayburt", "Karaman", "Kırıkkale", "Batman", "Şırnak",
                "Bartın", "Ardahan", "Iğdır", "Yalova", "Karabük", "Kilis", "Osmaniye", "Düzce"};
        for (int plate = 1; plate <= names.length; plate++) {
            NAMES.put(String.format("%02d", plate), names[plate - 1]);
        }
    }

    private TurkishProvinces() {
    }

    /** @param code iki haneli plaka kodu ({@code "35"}) */
    public static boolean exists(String code) {
        return code != null && NAMES.containsKey(code);
    }

    public static Optional<String> nameOf(String code) {
        return Optional.ofNullable(code == null ? null : NAMES.get(code));
    }
}
