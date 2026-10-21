package com.bahceden.order;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.web.ApiException;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Süresi dolan ayıplı ürün taleplerinin işi (ADR-083).
 *
 * <p>Bir tur, süresi dolmuş bütün talepleri en eski süreliden başlayarak
 * yapılandırılmış boyutta sayfalarla dolaşır; sonraki sayfa son görülen
 * talepten sonrasıyla okunur (ADR-100). Her talep kendi transaction'ında
 * kilitlenip yeniden denetlenir ({@link DefectClaimService#expire(long)}): bir
 * talebin hatası (örneğin iade yapılamadı) diğerlerini durdurmaz ve arkadaki
 * talepleri bekletmez; o talep değişmeden kalır ve sonraki turda yeniden
 * denenir. Bir turda her talep en çok bir kez denenir. Kullanıcı eylemiyle aynı anda işlenen talep çift
 * işlenmez: satır kilidinden sonra durum yeniden okunur.
 *
 * <p>Tek örnek varsayılır (AGENTS.md §3); iki örnek aynı talebi işlese de kilit
 * ve yeniden denetim çift iadeyi önler.
 */
@Component
public class DefectClaimSweep {

    private static final Logger log = LoggerFactory.getLogger(DefectClaimSweep.class);

    private final DefectClaimRepository claims;
    private final DefectClaimService service;
    private final int batchSize;
    private final Clock clock;

    DefectClaimSweep(DefectClaimRepository claims, DefectClaimService service, BahcedenProperties properties,
                     Clock clock) {
        this.claims = claims;
        this.service = service;
        this.batchSize = properties.claims().sweepBatchSize();
        this.clock = clock;
    }

    /**
     * Tek tur.
     *
     * @return ilerleyen talep sayısı
     */
    public int sweepOnce() {
        var now = clock.instant();
        var page = PageRequest.of(0, batchSize);
        int advanced = 0;
        int failed = 0;
        SweepCursor last = null;
        List<SweepCursor> batch;
        do {
            batch = last == null
                    ? claims.findOverdue(now, page)
                    : claims.findOverdueAfter(now, last.dueAt(), last.id(), page);
            for (var cursor : batch) {
                try {
                    if (service.expire(cursor.id()).isPresent()) {
                        advanced++;
                    }
                } catch (ApiException cause) {
                    failed++;
                    log.warn("Süresi dolan talep ilerletilemedi, sonraki turda yeniden denenecek: talep {}, {}",
                            cursor.id(), cause.getCode());
                }
                last = cursor;
            }
        } while (batch.size() == batchSize);
        if (failed > 0) {
            log.warn("Talep süre işi turu: {} ilerledi, {} hata", advanced, failed);
        }
        return advanced;
    }
}
