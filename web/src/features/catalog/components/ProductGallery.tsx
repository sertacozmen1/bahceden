"use client";

import Image from "next/image";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { cardSize, photoUrl, type ProductPhoto } from "@/lib/productPhotos";

/**
 * Ürün detayındaki galeri (storefront-catalog "Ürün detayı"): büyük görsel ve
 * küçük resimler. İstemci bileşeni yalnızca seçili fotoğrafı tutmak için;
 * görseller sunucuda boyutlandırılmıştır (`unoptimized`, D5). Küçük resimler
 * erişilebilir adlı düğmelerdir; seçili olan `aria-current` taşır.
 */
type Props = { readonly photos: readonly ProductPhoto[]; readonly name: string };

export function ProductGallery({ photos, name }: Props) {
  const t = useTranslations("storefront.detail");
  const [selected, setSelected] = useState(0);
  const current = photos[selected] ?? photos[0];
  if (current === undefined) {
    return null;
  }

  return (
    <section aria-label={t("galleryLabel")} className="flex flex-col gap-3">
      <div className="overflow-hidden rounded-xl bg-muted">
        <Image
          src={photoUrl(current.largeKey)}
          alt={name}
          width={current.width}
          height={current.height}
          unoptimized
          priority
          className="aspect-[4/3] h-auto w-full object-cover"
        />
      </div>
      {photos.length > 1 && (
        <ul className="grid grid-cols-5 gap-2">
          {photos.map((photo, index) => {
            const size = cardSize(photo);
            return (
              <li key={photo.id}>
                <button
                  type="button"
                  aria-label={t("showPhoto", { position: index + 1 })}
                  aria-current={index === selected ? "true" : undefined}
                  onClick={() => setSelected(index)}
                  className={cn(
                    "block w-full overflow-hidden rounded-lg ring-offset-2 ring-offset-background outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                    index === selected ? "ring-2 ring-primary" : "opacity-80 hover:opacity-100",
                  )}
                >
                  <Image
                    src={photoUrl(photo.cardKey)}
                    alt=""
                    width={size.width}
                    height={size.height}
                    unoptimized
                    className="aspect-square h-auto w-full object-cover"
                  />
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
