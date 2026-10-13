package com.bahceden.order;

import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.web.ApiException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Süresi dolan ayıplı ürün taleplerinin işi (ADR-083).
 *
 * <p>Bir turda en eski süreli taleplerden yapılandırılmış sayıda alınır. Her
 * talep kendi transaction'ında kilitlenip yeniden denetlenir
 * ({@link DefectClaimService#expire(long)}): bir talebin hatası (örneğin iade
 * yapılamadı) diğerlerini durdurmaz; o talep değişmeden kalır ve sonraki
 * turda yeniden denenir. Kullanıcı eylemiyle aynı anda işlenen talep çift
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
        var ids = claims.findOverdueIds(clock.instant(), PageRequest.of(0, batchSize));
        int advanced = 0;
        for (var id : ids) {
            try {
                if (service.expire(id).isPresent()) {
                    advanced++;
                }
            } catch (ApiException cause) {
                log.warn("Süresi dolan talep ilerletilemedi, sonraki turda yeniden denenecek: talep {}, {}", id,
                        cause.getCode());
            }
        }
        return advanced;
    }
}
