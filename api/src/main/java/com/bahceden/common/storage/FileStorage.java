package com.bahceden.common.storage;

import com.bahceden.common.BahcedenProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Yüklenen dosyaların diske yazılması ve okunması: tek sınıf (ADR-039,
 * add-seller-onboarding D6).
 *
 * <p>Ayrı bir depolama soyutlaması yazılmadı (AGENTS.md §3): nesne depolamaya
 * geçiş bu sınıfın değişmesidir. Dosya adı sunucuda üretilen rastgele bir
 * anahtardır; kullanıcının verdiği ad diske yansımaz. Anahtar biçimi okuma ve
 * silmede de doğrulanır, böylece dışarıdan gelen bir değer kök dizinin dışına
 * çıkamaz.
 *
 * <p>Kök dizin açılışta oluşturulur ve yazılabilirliği doğrulanır; değilse
 * uygulama <strong>başlamaz</strong>.
 */
@Component
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    /** 16 bayt = 128 bit: tahmin edilemez ve çakışmaz; 32 onaltılık karakter. */
    private static final int KEY_BYTES = 16;

    private static final Pattern KEY_FORMAT = Pattern.compile("[0-9a-f]{32}");

    private final Path root;
    private final SecureRandom random = new SecureRandom();

    FileStorage(BahcedenProperties properties) {
        this.root = Path.of(properties.storage().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException cause) {
            throw new IllegalStateException("Depolama dizini oluşturulamadı: bahceden.storage.dir", cause);
        }
        if (!Files.isWritable(root)) {
            throw new IllegalStateException("Depolama dizini yazılabilir değil: bahceden.storage.dir");
        }
    }

    /** İçeriğin ilk baytlarından tür tespiti; akış tüketilir. */
    public static Optional<FileType> detect(InputStream content) {
        try (content) {
            return FileType.detect(content.readNBytes(FileType.SIGNATURE_LENGTH));
        } catch (IOException cause) {
            throw new UncheckedIOException("Dosya okunamadı", cause);
        }
    }

    /**
     * İçeriği yeni bir anahtarla yazar. Önce geçici dosyaya yazılır, sonra
     * yerine taşınır: yarım yazılmış bir dosya hiçbir zaman geçerli anahtarla
     * görünmez.
     *
     * @return sunucunun ürettiği anahtar; veritabanına yalnızca bu yazılır
     */
    public String write(InputStream content) {
        var key = newKey();
        var target = root.resolve(key);
        var temporary = root.resolve(key + ".part");
        try (content) {
            Files.copy(content, temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch (IOException cause) {
            deleteQuietly(temporary);
            throw new UncheckedIOException("Dosya yazılamadı", cause);
        }
    }

    /** Dosyayı okumak için açar; akışı çağıran kapatır. */
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException cause) {
            throw new UncheckedIOException("Dosya okunamadı: " + key, cause);
        }
    }

    /**
     * Dosya varsa açar; anahtar biçimi geçersizse veya dosya yoksa boş döner.
     * Herkese açık uçlar dışarıdan gelen anahtarı bununla açar: geçersiz değer
     * hata değil "bulunamadı"dır.
     */
    public Optional<InputStream> openIfExists(String key) {
        if (!KEY_FORMAT.matcher(key).matches()) {
            return Optional.empty();
        }
        var path = root.resolve(key);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(path));
        } catch (IOException cause) {
            throw new UncheckedIOException("Dosya okunamadı: " + key, cause);
        }
    }

    /** İçeriği bellekteki baytlardan yazar (işlenmiş fotoğraflar); {@link #write(InputStream)} ile aynı garanti. */
    public String write(byte[] content) {
        return write(new ByteArrayInputStream(content));
    }

    /**
     * İçeriği yazar ve çevreleyen transaction commit'lenmezse dosyayı siler:
     * kaydı oluşmayan dosya diskte yetim kalmaz. İlan fotoğrafı ve ayıplı ürün
     * talebi fotoğrafı aynı garantiyi kullanır (K9).
     *
     * @throws IllegalStateException etkin bir transaction yoksa
     */
    public String writeWithinTransaction(byte[] content) {
        var key = write(content);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    delete(key);
                }
            }
        });
        return key;
    }

    /**
     * Dosyayı siler. Silme başarısızsa hata fırlatılmaz, loglanır: çağıran
     * (transaction geri alma) zaten bir hatayı işliyor; yetim dosyanın zararı
     * disk alanından ibarettir (design.md Risks).
     */
    public void delete(String key) {
        deleteQuietly(resolve(key));
    }

    private Path resolve(String key) {
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new IllegalArgumentException("Geçersiz dosya anahtarı");
        }
        return root.resolve(key);
    }

    private String newKey() {
        var bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cause) {
            log.warn("Dosya silinemedi, yetim kaldı: {}", path.getFileName(), cause);
        }
    }
}
