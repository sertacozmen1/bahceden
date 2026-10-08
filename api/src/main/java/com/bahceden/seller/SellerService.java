package com.bahceden.seller;

import com.bahceden.account.AccountRepository;
import com.bahceden.account.domain.Account;
import com.bahceden.account.domain.AccountRole;
import com.bahceden.common.BahcedenProperties;
import com.bahceden.common.i18n.Messages;
import com.bahceden.common.mail.MailSender;
import com.bahceden.common.storage.FileStorage;
import com.bahceden.common.storage.FileType;
import com.bahceden.common.text.Slugs;
import com.bahceden.common.web.ApiErrorCode;
import com.bahceden.common.web.ApiException;
import com.bahceden.common.web.PageResponse;
import com.bahceden.seller.domain.Seller;
import com.bahceden.seller.domain.SellerDocument;
import com.bahceden.seller.domain.SellerDocumentType;
import com.bahceden.seller.domain.SellerStatus;
import com.bahceden.seller.domain.SellerStatusChange;
import com.bahceden.seller.domain.SellerTransition;
import com.bahceden.seller.dto.AdminSellerDetail;
import com.bahceden.seller.dto.AdminSellerSummary;
import com.bahceden.seller.dto.SellerApplicationRequest;
import com.bahceden.seller.dto.SellerSelfView;
import com.bahceden.seller.dto.SellerStatusChangeView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Satıcı başvurusu ve satıcı kaydının iş kuralları (ADR-037, add-seller-onboarding
 * D1–D6).
 *
 * <p>Hesap kimliği her zaman token'dan gelir; istemci satıcı kimliği göndermez.
 * Kayda yalnızca {@link SellerRepository#findByAccountId} ile ulaşılır
 * (ADR-007).
 *
 * <p>Admin kararları (D5, D7) tek bir geçiş metodundan geçer: durum, rol ve
 * geçmiş kaydı aynı transaction'da değişir; karar hesabın dilinde e-postayla
 * bildirilir ve gönderim hatası kararı geri almaz (ADR-019).
 *
 * <p>Kişisel veri log'a yazılmaz: telefonun yalnızca son 4 hanesi, belgenin
 * yalnızca sunucunun ürettiği anahtarı loglanır (K10).
 */
@Service
public class SellerService {

    private static final Logger log = LoggerFactory.getLogger(SellerService.class);

    /** Türkiye ulusal numarası: 10 hane, 0 ile başlamaz. */
    private static final Pattern NATIONAL_NUMBER = Pattern.compile("[1-9]\\d{9}");

    /** Telefonda kullanıcının yazabileceği ayraçlar: boşluk, tire, nokta, parantez. */
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s\\-.()]");

    private static final String COUNTRY_PREFIX = "+90";

    /** Ad yalnızca Latin dışı karakterlerden oluşuyorsa kullanılan taban. */
    private static final String SLUG_FALLBACK = "uretici";

    private static final int PHONE_VISIBLE_DIGITS = 4;

    /** Admin listesinin sayfa üst sınırı: vitrinle aynı (AGENTS.md §11.5). */
    static final int MAX_PAGE_SIZE = 50;

    /** Karar e-postalarının anahtar öneki; geçiş başına bir şablon. */
    private static final Map<SellerTransition, String> DECISION_MAIL = Map.of(
            SellerTransition.APPROVE, "mail.sellerApproved",
            SellerTransition.REJECT, "mail.sellerRejected",
            SellerTransition.SUSPEND, "mail.sellerSuspended",
            SellerTransition.REINSTATE, "mail.sellerReinstated");

    private static final String APPLICATION_PAGE = "/seller/application";

    private final SellerRepository sellers;
    private final SellerDocumentRepository documents;
    private final SellerStatusChangeRepository statusChanges;
    private final AccountRepository accounts;
    private final SellerMapper mapper;
    private final FileStorage storage;
    private final MailSender mailSender;
    private final Messages messages;
    private final BahcedenProperties properties;
    private final Clock clock;

    SellerService(SellerRepository sellers,
                  SellerDocumentRepository documents,
                  SellerStatusChangeRepository statusChanges,
                  AccountRepository accounts,
                  SellerMapper mapper,
                  FileStorage storage,
                  MailSender mailSender,
                  Messages messages,
                  BahcedenProperties properties,
                  Clock clock) {
        this.sellers = sellers;
        this.documents = documents;
        this.statusChanges = statusChanges;
        this.accounts = accounts;
        this.mapper = mapper;
        this.storage = storage;
        this.mailSender = mailSender;
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Yeni başvuru: kayıt {@code PENDING} açılır, belge saklanır, geçiş kaydı
     * yazılır (D1, D3). E-postası doğrulanmamış hesap başvuramaz (D4).
     *
     * @throws ApiException {@code SELLER_EMAIL_NOT_VERIFIED}, {@code SELLER_ALREADY_EXISTS}
     *                      veya alan bazlı {@code VALIDATION_FAILED}
     */
    @Transactional
    public SellerSelfView apply(long accountId, SellerApplicationRequest request) {
        var account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_REQUIRED, "error.authRequired"));
        if (!account.isEmailVerified()) {
            throw new ApiException(ApiErrorCode.SELLER_EMAIL_NOT_VERIFIED, "error.sellerEmailNotVerified");
        }
        if (sellers.findByAccountId(accountId).isPresent()) {
            throw new ApiException(ApiErrorCode.SELLER_ALREADY_EXISTS, "error.sellerAlreadyExists");
        }
        var phone = normalizePhone(request.phone());
        var fileType = checkDocument(request.document());

        var now = clock.instant();
        var seller = sellers.save(Seller.applyFor(accountId, uniqueSlug(request.displayName()),
                request.displayName().trim(), request.city().trim(), phone, now));
        var document = storeDocument(seller, request, fileType, now);
        statusChanges.save(SellerStatusChange.record(
                seller.getId(), null, SellerStatus.PENDING, null, accountId, now));

        log.info("Satıcı başvurusu alındı: hesap {}, satıcı {}, telefon {}",
                accountId, seller.getId(), maskedPhone(phone));
        return mapper.toSelfView(seller, document);
    }

    /**
     * Reddedilen başvurunun yeniden gönderimi: beyan güncellenir, yeni belge
     * eklenir, önceki belge denetim izi için kalır, kayıt {@code PENDING} olur.
     *
     * @throws ApiException {@code SELLER_NOT_FOUND}, {@code SELLER_INVALID_TRANSITION}
     *                      veya alan bazlı {@code VALIDATION_FAILED}
     */
    @Transactional
    public SellerSelfView resubmit(long accountId, SellerApplicationRequest request) {
        var seller = ownSeller(accountId);
        if (!seller.allows(SellerTransition.RESUBMIT)) {
            throw invalidTransition();
        }
        var phone = normalizePhone(request.phone());
        var fileType = checkDocument(request.document());

        var now = clock.instant();
        var previous = seller.getStatus();
        seller.updateDeclaration(request.displayName().trim(), request.city().trim(), phone, now);
        seller.apply(SellerTransition.RESUBMIT, null, now);
        var document = storeDocument(seller, request, fileType, now);
        statusChanges.save(SellerStatusChange.record(
                seller.getId(), previous, seller.getStatus(), null, accountId, now));

        log.info("Satıcı başvurusu yeniden gönderildi: hesap {}, satıcı {}", accountId, seller.getId());
        return mapper.toSelfView(seller, document);
    }

    /**
     * @throws ApiException {@code SELLER_NOT_FOUND}: hesabın satıcı kaydı yok
     */
    @Transactional(readOnly = true)
    public SellerSelfView findOwn(long accountId) {
        var seller = ownSeller(accountId);
        return mapper.toSelfView(seller, currentDocument(seller));
    }

    /**
     * Kaydın sahibine güncel belgesi. Sahiplik sorgu seviyesindedir: başka bir
     * satıcının belgesine bu yoldan ulaşmanın yolu yoktur.
     */
    @Transactional(readOnly = true)
    public SellerDocumentContent ownDocument(long accountId) {
        return open(currentDocument(ownSeller(accountId)));
    }

    /**
     * Satıcıya özgü her işlemin kapısı (D5): onaylı satıcıyı getirir. Rol
     * erişim token'ında en geç 15 dakika daha durabilir; durum burada anında
     * denetlenir.
     *
     * @throws ApiException {@code SELLER_NOT_APPROVED}
     */
    @Transactional(readOnly = true)
    public Seller requireApprovedSeller(long accountId) {
        return sellers.findByAccountIdAndStatus(accountId, SellerStatus.APPROVED)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SELLER_NOT_APPROVED, "error.sellerNotApproved"));
    }

    /**
     * Admin listesi: durumla süzülebilir, yeniden eskiye, sunucuda sınırlı sayfa.
     * Satırlar ilişki taşımaz; sorgu sayısı kayıt sayısından bağımsızdır.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminSellerSummary> listForAdmin(Optional<SellerStatus> status, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        var sellersPage = status
                .map(value -> sellers.findByStatusNewestFirst(value, pageable))
                .orElseGet(() -> sellers.findAllNewestFirst(pageable));
        return PageResponse.from(sellersPage.map(mapper::toAdminSummary));
    }

    /**
     * Admin detayı: beyan, iletişim, güncel belge türü ve geçmiş. Geçişleri
     * yapanların e-postaları tek sorguda okunur.
     *
     * @throws ApiException {@code SELLER_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public AdminSellerDetail detailForAdmin(long sellerId) {
        var seller = sellers.findById(sellerId).orElseThrow(SellerService::sellerNotFound);
        var changes = statusChanges.findBySellerIdOrderByCreatedAtAscIdAsc(sellerId);

        var accountIds = changes.stream().map(SellerStatusChange::getActorAccountId).collect(Collectors.toSet());
        seller.getAccountId().ifPresent(accountIds::add);
        var emails = accounts.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Account::getEmail));

        var history = changes.stream()
                .map(change -> new SellerStatusChangeView(
                        change.getFromStatus().orElse(null),
                        change.getToStatus(),
                        change.getReason().orElse(null),
                        emails.get(change.getActorAccountId()),
                        change.getCreatedAt()))
                .toList();
        return mapper.toAdminDetail(seller,
                documents.findFirstBySellerIdOrderByCreatedAtDescIdDesc(sellerId),
                seller.getAccountId().map(emails::get),
                history);
    }

    /**
     * Admin'e güncel belge. Belgesi olmayan (seed) kayıtta da 404 döner.
     *
     * @throws ApiException {@code SELLER_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public SellerDocumentContent documentForAdmin(long sellerId) {
        var seller = sellers.findById(sellerId).orElseThrow(SellerService::sellerNotFound);
        return documents.findFirstBySellerIdOrderByCreatedAtDescIdDesc(seller.getId())
                .map(this::open)
                .orElseThrow(SellerService::sellerNotFound);
    }

    /**
     * Admin kararı (D5): onay, ret, askı veya askıdan çıkarma. Durum, rol ve
     * geçmiş kaydı aynı transaction'da değişir; karar hesabın dilinde
     * e-postayla bildirilir. Seed'den gelen hesapsız kayıtta rol ve e-posta
     * adımı atlanır.
     *
     * @param reason ret ve askıda zorunlu (controller doğrular), diğerlerinde {@code null}
     * @throws ApiException {@code SELLER_NOT_FOUND} veya {@code SELLER_INVALID_TRANSITION}
     */
    @Transactional
    public AdminSellerDetail decide(long adminAccountId, long sellerId, SellerTransition transition, String reason) {
        if (transition == SellerTransition.RESUBMIT) {
            throw new IllegalArgumentException("Yeniden gönderim admin kararı değildir");
        }
        var seller = sellers.findById(sellerId).orElseThrow(SellerService::sellerNotFound);
        if (!seller.allows(transition)) {
            throw invalidTransition();
        }
        var now = clock.instant();
        var previous = seller.getStatus();
        var trimmedReason = reason == null ? null : reason.trim();
        seller.apply(transition, trimmedReason, now);
        statusChanges.save(SellerStatusChange.record(
                seller.getId(), previous, seller.getStatus(), trimmedReason, adminAccountId, now));

        seller.getAccountId().flatMap(accounts::findById).ifPresent(owner -> {
            if (transition == SellerTransition.APPROVE || transition == SellerTransition.REINSTATE) {
                owner.grantRole(AccountRole.ROLE_SELLER, now);
            } else if (transition == SellerTransition.SUSPEND) {
                owner.revokeRole(AccountRole.ROLE_SELLER, now);
            }
            notifyDecision(owner, seller, transition, trimmedReason);
        });

        log.info("Satıcı kararı: satıcı {}, {} -> {}, admin {}", seller.getId(), previous, seller.getStatus(),
                adminAccountId);
        return detailForAdmin(seller.getId());
    }

    private void notifyDecision(Account owner, Seller seller, SellerTransition transition, String reason) {
        var prefix = DECISION_MAIL.get(transition);
        var locale = owner.getLocale();
        var link = properties.webOrigin() + APPLICATION_PAGE;
        var body = transition.requiresReason()
                ? messages.get(prefix + ".body", locale, seller.getDisplayName(), reason, link)
                : messages.get(prefix + ".body", locale, seller.getDisplayName(), link);
        if (!mailSender.send(owner.getEmail(), messages.get(prefix + ".subject", locale), body)) {
            log.warn("Satıcı karar e-postası gönderilemedi; karar geçerli: satıcı {}", seller.getId());
        }
    }

    private Seller ownSeller(long accountId) {
        return sellers.findByAccountId(accountId).orElseThrow(SellerService::sellerNotFound);
    }

    private SellerDocument currentDocument(Seller seller) {
        // Hesabı olan her kaydın en az bir belgesi vardır (başvuru belgesiz açılmaz).
        return documents.findFirstBySellerIdOrderByCreatedAtDescIdDesc(seller.getId())
                .orElseThrow(() -> new IllegalStateException("Belgesiz satıcı kaydı: " + seller.getId()));
    }

    private SellerDocumentContent open(SellerDocument document) {
        return new SellerDocumentContent(FileType.fromContentType(document.getContentType()),
                document.getSizeBytes(), storage.open(document.getStorageKey()));
    }

    /**
     * Dosya doğrulaması (D6): boş olmamalı, sınırı aşmamalı, içeriği PDF, JPEG
     * veya PNG olmalı. İstemcinin bildirdiği tür ve dosya adı yok sayılır.
     */
    private FileType checkDocument(MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiException.invalidField("document", "validation.document.required");
        }
        if (file.getSize() > properties.storage().maxDocumentBytes()) {
            throw ApiException.invalidField("document", "validation.document.tooLarge");
        }
        try {
            return FileStorage.detect(file.getInputStream())
                    .orElseThrow(() -> ApiException.invalidField("document", "validation.document.type"));
        } catch (IOException cause) {
            throw new UncheckedIOException("Yüklenen dosya okunamadı", cause);
        }
    }

    /**
     * Dosyayı diske yazar ve meta veriyi kaydeder. Transaction geri alınırsa
     * yazılan dosya silinir (D3): veritabanında karşılığı olmayan dosya kalmaz.
     */
    private SellerDocument storeDocument(Seller seller, SellerApplicationRequest request, FileType type,
                                         Instant now) {
        String key;
        try {
            key = storage.write(request.document().getInputStream());
        } catch (IOException cause) {
            throw new UncheckedIOException("Yüklenen dosya okunamadı", cause);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
        return documents.save(SellerDocument.of(seller.getId(),
                SellerDocumentType.valueOf(request.documentType()), key, type.contentType(),
                request.document().getSize(), now));
    }

    /**
     * Türkiye numarasını E.164'e çevirir: ayraçlar temizlenir, {@code 0},
     * {@code 90} veya {@code +90} öneki kabul edilir.
     */
    private static String normalizePhone(String raw) {
        var digits = PHONE_SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (digits.startsWith(COUNTRY_PREFIX)) {
            digits = digits.substring(COUNTRY_PREFIX.length());
        } else if (digits.startsWith("90") && digits.length() == 12) {
            digits = digits.substring(2);
        } else if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!NATIONAL_NUMBER.matcher(digits).matches()) {
            throw ApiException.invalidField("phone", "validation.phone.format");
        }
        return COUNTRY_PREFIX + digits;
    }

    /**
     * Üretici adından vitrin adresi: Türkçe karakterler ASCII'ye çevrilir,
     * çakışmada sona {@code -2}, {@code -3} … eklenir.
     */
    private String uniqueSlug(String displayName) {
        var base = Slugs.slugify(displayName, SLUG_FALLBACK);
        var candidate = base;
        for (int suffix = 2; sellers.existsBySlug(candidate); suffix++) {
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private static String maskedPhone(String phone) {
        return "***" + phone.substring(phone.length() - PHONE_VISIBLE_DIGITS);
    }

    static ApiException sellerNotFound() {
        return new ApiException(ApiErrorCode.SELLER_NOT_FOUND, "error.sellerNotFound");
    }

    static ApiException invalidTransition() {
        return new ApiException(ApiErrorCode.SELLER_INVALID_TRANSITION, "error.sellerInvalidTransition");
    }
}
