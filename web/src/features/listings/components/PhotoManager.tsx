"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { ArrowDown, ArrowUp, ImagePlus, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { FormMessage } from "@/features/account/components/FormMessage";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import type { SellerListingView } from "@/features/listings/api";
import { listingBffPaths } from "@/features/listings/bff";
import { postToBff } from "@/lib/bffClient";
import { cardSize, photoUrl, type ProductPhoto } from "@/lib/productPhotos";

/**
 * İlan fotoğrafları (product-listings spec'i "İlan fotoğrafları", D3).
 *
 * Fotoğraflar tek tek yüklenir: sahada telefonla çekilen fotoğraf hemen
 * eklenir. Sıra sürükle-bırak yerine öne / arkaya düğmeleriyle değişir: klavye
 * ve dokunmayla aynı şekilde erişilir. Silme geri alınamaz, onay ister.
 * İstemcideki tür ve boyut denetimi yalnızca erken uyarıdır; otorite `api`'dir.
 */

const MAX_PHOTOS = 5;
const MAX_PHOTO_BYTES = 10 * 1024 * 1024;
const ACCEPTED_TYPES = ["image/jpeg", "image/png"] as const;

type Props = { readonly listing: SellerListingView };

export function PhotoManager({ listing }: Props) {
  const t = useTranslations("listings.photos");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const fileInput = useRef<HTMLInputElement>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const photos = listing.photos;
  const full = photos.length >= MAX_PHOTOS;

  async function run(action: () => Promise<unknown>) {
    setPending(true);
    setError(null);
    try {
      await action();
      router.refresh();
    } catch (cause) {
      const mapped = toFormErrors(cause, errorTexts);
      setError(mapped.fields.photo ?? mapped.fields.photoIds ?? mapped.general ?? errorTexts.unavailable);
    } finally {
      setPending(false);
    }
  }

  async function upload(file: File) {
    if (file.size > MAX_PHOTO_BYTES) {
      setError(t("tooLarge"));
      return;
    }
    if (file.type !== "" && !ACCEPTED_TYPES.some((type) => type === file.type)) {
      setError(t("wrongType"));
      return;
    }
    const form = new FormData();
    form.set("photo", file);
    await run(() => postToBff(listingBffPaths.photos(listing.id), form));
    if (fileInput.current !== null) {
      fileInput.current.value = "";
    }
  }

  function move(index: number, offset: -1 | 1) {
    const ids = photos.map((photo) => photo.id);
    const [moved] = ids.splice(index, 1);
    if (moved === undefined) {
      return;
    }
    ids.splice(index + offset, 0, moved);
    void run(() => postToBff(listingBffPaths.photoOrder(listing.id), { photoIds: ids }, "PUT"));
  }

  return (
    <section aria-labelledby="photos-title" className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h2 id="photos-title" className="text-base font-semibold tracking-tight">
          {t("title")}
        </h2>
        <p className="text-sm text-pretty text-muted-foreground">{t("intro")}</p>
      </div>
      {error !== null && <FormMessage tone="error">{error}</FormMessage>}
      {photos.length === 0 ? (
        <p className="rounded-xl border border-dashed px-4 py-8 text-center text-sm text-muted-foreground">{t("empty")}</p>
      ) : (
        <ol className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {photos.map((photo, index) => (
            <PhotoTile
              key={photo.id}
              photo={photo}
              position={index + 1}
              name={listing.name}
              isFirst={index === 0}
              isLast={index === photos.length - 1}
              disabled={pending}
              onMove={(offset) => move(index, offset)}
              onDelete={() => run(() => postToBff(listingBffPaths.photo(listing.id, photo.id), undefined, "DELETE"))}
            />
          ))}
        </ol>
      )}
      {full ? (
        <p className="text-sm text-muted-foreground">{t("limitReached")}</p>
      ) : (
        <div className="flex flex-col gap-2">
          <label htmlFor="listing-photo" className="sr-only">
            {t("fileLabel")}
          </label>
          <input
            ref={fileInput}
            id="listing-photo"
            type="file"
            accept={ACCEPTED_TYPES.join(",")}
            className="sr-only"
            onChange={(event) => {
              const file = event.currentTarget.files?.[0];
              if (file !== undefined) {
                void upload(file);
              }
            }}
          />
          <Button
            type="button"
            variant="secondary"
            className="self-start"
            disabled={pending}
            onClick={() => fileInput.current?.click()}
          >
            <ImagePlus aria-hidden />
            {pending ? t("uploading") : t("add")}
          </Button>
          <p className="text-sm text-muted-foreground">{t("hint")}</p>
        </div>
      )}
    </section>
  );
}

type TileProps = {
  readonly photo: ProductPhoto;
  readonly position: number;
  readonly name: string;
  readonly isFirst: boolean;
  readonly isLast: boolean;
  readonly disabled: boolean;
  readonly onMove: (offset: -1 | 1) => void;
  readonly onDelete: () => void;
};

function PhotoTile({ photo, position, name, isFirst, isLast, disabled, onMove, onDelete }: TileProps) {
  const t = useTranslations("listings.photos");
  const size = cardSize(photo);
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-border bg-card p-2">
      <div className="relative overflow-hidden rounded-lg bg-muted">
        <Image
          src={photoUrl(photo.cardKey)}
          alt={t("alt", { name, position })}
          width={size.width}
          height={size.height}
          unoptimized
          className="aspect-square h-auto w-full object-cover"
        />
        {isFirst && (
          <Badge className="absolute top-2 left-2" variant="default">
            {t("cover")}
          </Badge>
        )}
      </div>
      <div className="flex items-center justify-between gap-1">
        <div className="flex gap-1">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={t("moveUp", { position })}
            disabled={disabled || isFirst}
            onClick={() => onMove(-1)}
          >
            <ArrowUp aria-hidden />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={t("moveDown", { position })}
            disabled={disabled || isLast}
            onClick={() => onMove(1)}
          >
            <ArrowDown aria-hidden />
          </Button>
        </div>
        <Dialog>
          <DialogTrigger
            render={<Button type="button" variant="ghost" size="icon" aria-label={t("delete", { position })} disabled={disabled} />}
          >
            <Trash2 aria-hidden className="text-destructive" />
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{t("deleteTitle")}</DialogTitle>
              <DialogDescription>{t("deleteBody")}</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <DialogClose render={<Button variant="ghost" type="button" />}>{t("cancel")}</DialogClose>
              <DialogClose render={<Button variant="destructive" type="button" onClick={onDelete} />}>
                {t("deleteConfirm")}
              </DialogClose>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </li>
  );
}
