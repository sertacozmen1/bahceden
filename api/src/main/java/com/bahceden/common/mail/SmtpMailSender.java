package com.bahceden.common.mail;

import com.bahceden.common.BahcedenProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP üzerinden e-posta gönderimi (design.md D2).
 *
 * <p>Sağlayıcıdan bağımsızdır: her sağlayıcı SMTP konuşur, dolayısıyla sağlayıcı
 * kararı (KALANLAR B4/B5) bu koddan ayrılmıştır. Geliştirmede hedef
 * {@code mailpit}, üretimde gerçek SMTP sunucusu; ikisi de ortam
 * değişkenlerinden gelir (K10).
 *
 * <p>Gönderim hatası fırlatılmaz: kayıt akışı e-posta teslimine bağımlı
 * değildir (ADR-017 §4). Hata {@code WARN} olarak loglanır ve alıcı adresi
 * <strong>maskelenir</strong> (K10).
 */
@Component
public class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender javaMailSender;
    private final BahcedenProperties properties;

    SmtpMailSender(JavaMailSender javaMailSender, BahcedenProperties properties) {
        this.javaMailSender = javaMailSender;
        this.properties = properties;
    }

    @Override
    public boolean send(String to, String subject, String body) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            javaMailSender.send(message);
            log.debug("E-posta gönderildi: {} ({})", mask(to), subject);
            return true;
        } catch (MailException cause) {
            // Yutmuyoruz ama fırlatmıyoruz: kullanıcı akışı e-postaya bağlı değil.
            log.warn("E-posta gönderilemedi: {} ({})", mask(to), subject, cause);
            return false;
        }
    }

    /**
     * Kişisel veri log'a maskeli yazılır (K10): {@code ornek@alan.test} →
     * {@code o***@alan.test}.
     */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
