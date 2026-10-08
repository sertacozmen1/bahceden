package com.bahceden;

import static org.assertj.core.api.Assertions.assertThat;

import com.bahceden.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Geliştirme verisinin yalnızca dev bağlamında yüklendiğini sabitler
 * (ADR-014, ADR-016).
 *
 * <p>Bu test bir regresyondan doğdu: changeset önce {@code context:dev} ile
 * işaretlenmişti ama Liquibase bu öznitelik adını tanımadığı için filtre
 * uygulanmadı ve seed <strong>test profilinde de çalıştı</strong>. Doğru ad
 * {@code contextFilter}. Yanlış yapılandırma sessizce geçtiği için kuralın
 * kendisi testle korunuyor.
 *
 * <p>Tablo içeriği yerine <strong>changelog kaydı</strong> sorgulanıyor: diğer
 * testler aynı konteyneri paylaşıp veri yazdığı için satır saymak kırılgan
 * olurdu; uygulanan changeset listesi ise deterministiktir.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("Geliştirme verisi izolasyonu")
class DevSeedIsolationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Şema changeset'i uygulanır")
    void sema_changeseti_uygulanir() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from databasechangelog where id = ?", Integer.class, "001-create-catalog");

        assertThat(applied).isEqualTo(1);
    }

    @Test
    @DisplayName("Dev seed changeset'i test bağlamında uygulanmaz")
    void dev_seed_test_baglaminda_uygulanmaz() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from databasechangelog where id = ?", Integer.class, "002-dev-seed-catalog");

        assertThat(applied)
                .as("dev seed yalnızca contextFilter:dev etkinken çalışmalı (ADR-014)")
                .isZero();
    }

    @Test
    @DisplayName("Dev hesap seed'i test bağlamında uygulanmaz")
    void dev_hesap_seedi_test_baglaminda_uygulanmaz() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from databasechangelog where id = ?", Integer.class, "004-dev-seed-account");

        assertThat(applied)
                .as("sabit parolalı geliştirme hesapları yalnızca dev bağlamında oluşmalı (ADR-014)")
                .isZero();
    }

    @Test
    @DisplayName("Dev admin ve üretici hesabı seed'i test bağlamında uygulanmaz")
    void dev_admin_seedi_test_baglaminda_uygulanmaz() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from databasechangelog where id = ?", Integer.class, "008-dev-seed-seller-onboarding");

        assertThat(applied)
                .as("ROLE_ADMIN taşıyan sabit parolalı hesap yalnızca dev bağlamında oluşmalı (ADR-014)")
                .isZero();
    }
}
