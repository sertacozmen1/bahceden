import type { components } from "@contract";

/**
 * İlan fotoğraflarının adresi ve ölçüleri (add-product-listings D5).
 *
 * Sözleşme dosya yolu değil anahtar taşır; adresi `web` kurar. Tarayıcı
 * `api`'ye konuşmaz: fotoğraf `/media/product-photos/[key]` Route Handler'ı
 * üzerinden gelir. Görseller sunucuda boyutlandırılmıştır, `next/image`
 * `unoptimized` kullanılır; ölçüler yerleşim sıçramasın diye verilir.
 */
export type ProductPhoto = components["schemas"]["ProductPhotoView"];

/** Sunucunun kart boyutunun uzun kenarı (PhotoProcessor.CARD_EDGE ile aynı). */
const CARD_EDGE = 400;

export function photoUrl(key: string): string {
  return `/media/product-photos/${encodeURIComponent(key)}`;
}

/** Kart boyutunun ölçüleri: büyük boyutla aynı oranda, uzun kenar en çok 400 px. */
export function cardSize(photo: ProductPhoto): { readonly width: number; readonly height: number } {
  const longest = Math.max(photo.width, photo.height);
  if (longest <= CARD_EDGE) {
    return { width: photo.width, height: photo.height };
  }
  const ratio = CARD_EDGE / longest;
  return { width: Math.max(1, Math.round(photo.width * ratio)), height: Math.max(1, Math.round(photo.height * ratio)) };
}
