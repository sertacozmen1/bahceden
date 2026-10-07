import type { components } from "@contract";

/**
 * Kullanıcıya görünen tüm metinler (AGENTS.md §12.4).
 *
 * Bileşen içine serpiştirilmiş hardcode metin yasaktır: metnin tek yerde
 * durması hem üslup tutarlılığını korur hem de çoklu dil kararı (KALANLAR A4)
 * geldiğinde kapıyı açık tutar. Bugün tek dil: Türkçe.
 */

type ProductUnit = components["schemas"]["ProductSummary"]["unit"];
type AccountRole = components["schemas"]["AccountView"]["roles"][number];

export const texts = {
  siteName: "Bahçeden",
  siteTagline: "Üreticiden doğrudan, aradan hal çıkmış.",

  storefront: {
    title: "Vitrin",
    subtitle: "Üreticiden doğrudan gönderilen taze ürünler",
    emptyTitle: "Vitrinde henüz ürün yok",
    emptyBody: "Üreticiler ilk ürünlerini eklediğinde burada görünecek.",
    loading: "Ürünler yükleniyor",
    outOfStock: "Stokta yok",
    stockLeft: (quantity: number) => `Son ${quantity} adet`,
    inStock: "Stokta",
    perUnit: (unit: ProductUnit) => `/ ${unitLabels[unit]}`,
    sellerFrom: (city: string) => `${city} üreticisi`,
    resultCount: (total: number) => `${total} ürün`,
  },

  pagination: {
    previous: "Önceki sayfa",
    next: "Sonraki sayfa",
    current: (page: number, totalPages: number) => `Sayfa ${page} / ${totalPages}`,
  },

  errors: {
    title: "Ürünler yüklenemedi",
    body: "Sunucuya ulaşamadık. Bağlantınızı kontrol edip yeniden deneyebilirsiniz.",
    retry: "Yeniden dene",
  },

  account: {
    nav: {
      signIn: "Giriş yap",
      register: "Kayıt ol",
      myAccount: "Hesabım",
      signOut: "Çıkış yap",
      signingOut: "Çıkış yapılıyor…",
      signedInAs: (maskedEmail: string) => `${maskedEmail} olarak giriş yapıldı`,
    },
    fields: {
      email: "E-posta adresi",
      password: "Parola",
      passwordHint: "En az 10 karakter",
      code: "Giriş kodu",
      codeHint: "E-postanıza gelen 6 haneli kod",
    },
    register: {
      title: "Kayıt ol",
      intro: "E-posta adresiniz ve bir parolayla hesap açın. Hesabınızı hemen kullanabilirsiniz.",
      submit: "Hesap aç",
      submitting: "Hesap açılıyor…",
      successTitle: "Hesabınız hazır",
      goToSignIn: "Giriş yap",
      haveAccount: "Zaten hesabınız var mı?",
    },
    login: {
      title: "Giriş yap",
      intro: "E-posta adresiniz ve parolanızla giriş yapın.",
      submit: "Giriş yap",
      submitting: "Giriş yapılıyor…",
      codeTitle: "Giriş kodunu girin",
      codeSubmit: "Kodu onayla",
      codeSubmitting: "Kod doğrulanıyor…",
      startOver: "Baştan başla",
      noAccount: "Hesabınız yok mu?",
      signInRequired: "Bu sayfayı görmek için giriş yapın.",
    },
    verification: {
      title: "E-posta doğrulama",
      successTitle: "E-posta adresiniz doğrulandı",
      successBody: "Teşekkürler. Hesabınızı kullanmaya devam edebilirsiniz.",
      failedTitle: "Bağlantı kullanılamadı",
      missingToken:
        "Bağlantı eksik görünüyor. E-postadaki bağlantıyı tam olarak açtığınızdan emin olun ya da Hesabım sayfasından yeni bir bağlantı isteyin.",
      goToAccount: "Hesabıma git",
      goToStorefront: "Vitrine dön",
    },
    myAccount: {
      title: "Hesabım",
      email: "E-posta",
      roles: "Roller",
      verificationStatus: "E-posta doğrulaması",
      verified: "Doğrulandı",
      notVerified: "Doğrulanmadı",
      notVerifiedBody: "E-posta adresinizi doğrulamadınız. Hesabınızı kullanmanız engellenmez.",
      resend: "Yeni doğrulama bağlantısı gönder",
      resending: "Gönderiliyor…",
      resent: "Yeni bağlantıyı e-posta adresinize gönderdik.",
      loading: "Hesap bilgileri yükleniyor",
    },
    errors: {
      title: "Bir sorun oluştu",
      body: "İsteğinizi şu anda tamamlayamadık. Biraz sonra yeniden deneyebilirsiniz.",
      retry: "Yeniden dene",
      unavailable: "Sunucuya ulaşamadık. Bağlantınızı kontrol edip yeniden deneyin.",
      crossSite: "Bu istek başka bir siteden geldiği için reddedildi.",
      invalidRequest: "İstek okunamadı.",
      formHasErrors: "Formdaki işaretli alanları düzeltin.",
    },
  },

  footer: {
    copyright: "Bahçeden",
  },

  theme: {
    label: "Tema",
    trigger: (current: string) => `Tema: ${current}`,
    light: "Açık",
    dark: "Koyu",
    system: "Sistem",
  },

  notFound: {
    title: "Sayfa bulunamadı",
    body: "Aradığınız sayfa taşınmış veya hiç var olmamış olabilir.",
    backToStorefront: "Vitrine dön",
  },
} as const;

/** Satış biriminin kullanıcıya gösterilen karşılığı. */
const unitLabels: Record<NonNullable<ProductUnit>, string> = {
  KILOGRAM: "kg",
  PIECE: "adet",
  BUNCH: "demet",
};

/** Hesap rolünün kullanıcıya gösterilen karşılığı. */
const roleLabels: Record<AccountRole, string> = {
  ROLE_CUSTOMER: "Alıcı",
  ROLE_SELLER: "Satıcı",
  ROLE_ADMIN: "Yönetici",
};

export { roleLabels, unitLabels };
