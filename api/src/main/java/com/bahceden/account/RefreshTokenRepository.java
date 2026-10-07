package com.bahceden.account;

import com.bahceden.account.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Yenileme token'ı kayıtları (design.md D1).
 *
 * <p>Bugün yalnızca girişte yazılır; hash ile arama, rotasyon ve aile iptali
 * yenileme ucuyla birlikte gelir.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
