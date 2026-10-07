package com.bahceden.common.mail;

/**
 * E-posta gönderimi (design.md D2).
 *
 * <p>Arayüz olmasının sebebi testtir: testler gerçek SMTP'ye çıkmaz (AGENTS.md
 * K10) ve kaydeden bir uygulama ile değiştirilir. Geliştirmede SMTP hedefi
 * {@code mailpit}'tir.
 *
 * <p>Gönderim başarısızlığı çağıran akışı <strong>kesmez</strong> (ADR-017 §4):
 * uygulamalar hatayı yutmaz ama fırlatmaz da; kaydeder ve {@code false} döner.
 * Bu, e-posta teslimini kimliğin ön koşulu olmaktan çıkarır.
 */
public interface MailSender {

    /**
     * @param to      alıcı adresi
     * @param subject konu, alıcı hesabın dilinde (ADR-032)
     * @param body    düz metin gövde; şablon motoru kullanılmaz (D2)
     * @return gönderim başarılıysa {@code true}; başarısızlıkta {@code false}
     *         döner ve uygulama hatayı kendi loglar
     */
    boolean send(String to, String subject, String body);
}
