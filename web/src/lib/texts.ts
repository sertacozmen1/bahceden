import type { components } from "@contract";

/**
 * Kullanıcıya görünen tüm metinler (AGENTS.md §12.4).
 *
 * Bileşen içine serpiştirilmiş hardcode metin yasaktır: metnin tek yerde
 * durması hem üslup tutarlılığını korur hem de çoklu dil kararı (KALANLAR A4)
 * geldiğinde kapıyı açık tutar. Bugün tek dil: Türkçe.
 */

type ProductUnit = components["schemas"]["ProductSummary"]["unit"];

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

export { unitLabels };
