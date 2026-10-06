package com.bahceden.support;

import com.bahceden.common.mail.MailSender;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Gönderilen e-postaları kaydeden {@link MailSender}.
 *
 * <p>Testler gerçek SMTP'ye <strong>çıkmaz</strong> (AGENTS.md K10). Bu uygulama
 * gönderimi kaydeder, böylece testler "hangi adrese ne gönderildi" sorusunu
 * doğrulayabilir; ayrıca {@link #failNextSends()} ile gönderim hatası
 * canlandırılabilir (ADR-017 §4: hata akışı kesmez).
 */
public class RecordingMailSender implements MailSender {

    /** Tek bir gönderim kaydı. */
    public record SentMail(String to, String subject, String body) {
    }

    private final List<SentMail> sent = new ArrayList<>();
    private boolean failing;

    @Override
    public synchronized boolean send(String to, String subject, String body) {
        if (failing) {
            // Gerçek uygulamanın sözleşmesi: hata fırlatmaz, false döner.
            return false;
        }
        sent.add(new SentMail(to, subject, body));
        return true;
    }

    public synchronized List<SentMail> sent() {
        return List.copyOf(sent);
    }

    public synchronized SentMail lastSent() {
        if (sent.isEmpty()) {
            throw new AssertionError("Hiç e-posta gönderilmedi");
        }
        return sent.get(sent.size() - 1);
    }

    public synchronized void failNextSends() {
        failing = true;
    }

    public synchronized void reset() {
        sent.clear();
        failing = false;
    }

    /**
     * Gerçek SMTP göndericisinin yerine geçer. {@code @Primary} olduğu için
     * uygulamanın {@code SmtpMailSender} bean'i yerine bu enjekte edilir.
     */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }
}
