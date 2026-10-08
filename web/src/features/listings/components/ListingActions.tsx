"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Archive, ArchiveRestore, ExternalLink, EyeOff, Rocket } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button, buttonVariants } from "@/components/ui/button";
import { FormMessage } from "@/features/account/components/FormMessage";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import type { ListingAction, SellerListingView } from "@/features/listings/api";
import { listingBffPaths } from "@/features/listings/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * Satıcının yayın eylemleri (product-listings spec'i "İlan yayın durumları",
 * D1). Yalnızca şimdiki durumdan yapılabilen eylemler görünür; otorite yine
 * `api`'dir. "Yayına al" birincil ve büyüktür; fotoğraf yoksa nedeni yazılır
 * ve düğme pasiftir. Kaldırılmış ilanda eylem yoktur (ADR-038 §3).
 */
type Props = { readonly listing: SellerListingView };

export function ListingActions({ listing }: Props) {
  const t = useTranslations("listings.actions");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [pending, setPending] = useState<ListingAction | null>(null);
  const [error, setError] = useState<string | null>(null);
  const hasPhoto = listing.photos.length > 0;

  async function run(action: ListingAction) {
    setPending(action);
    setError(null);
    try {
      await postToBff(listingBffPaths.action(listing.id, action));
      router.refresh();
    } catch (cause) {
      setError(toFormErrors(cause, errorTexts).general ?? errorTexts.unavailable);
    } finally {
      setPending(null);
    }
  }

  const body = {
    DRAFT: t("draftBody"),
    PUBLISHED: t("publishedBody"),
    ARCHIVED: t("archivedBody"),
    REMOVED: t("removedBody"),
  }[listing.status];

  return (
    <section aria-labelledby="actions-title" className="flex flex-col gap-3">
      <h2 id="actions-title" className="text-base font-semibold tracking-tight">
        {t("title")}
      </h2>
      <p className="text-sm text-pretty text-muted-foreground">{body}</p>
      {error !== null && <FormMessage tone="error">{error}</FormMessage>}
      {listing.status === "DRAFT" && !hasPhoto && <FormMessage tone="info">{t("needsPhoto")}</FormMessage>}
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        {listing.status === "DRAFT" && (
          <Button size="lg" type="button" disabled={pending !== null || !hasPhoto} onClick={() => void run("publish")}>
            <Rocket aria-hidden />
            {pending === "publish" ? t("working") : t("publish")}
          </Button>
        )}
        {listing.status === "PUBLISHED" && (
          <>
            <Link className={buttonVariants({ variant: "outline" })} href={`/products/${listing.slug}`}>
              <ExternalLink aria-hidden />
              {t("viewInStore")}
            </Link>
            <Button variant="secondary" type="button" disabled={pending !== null} onClick={() => void run("unpublish")}>
              <EyeOff aria-hidden />
              {pending === "unpublish" ? t("working") : t("unpublish")}
            </Button>
          </>
        )}
        {(listing.status === "DRAFT" || listing.status === "PUBLISHED") && (
          <Button variant="ghost" type="button" disabled={pending !== null} onClick={() => void run("archive")}>
            <Archive aria-hidden />
            {pending === "archive" ? t("working") : t("archive")}
          </Button>
        )}
        {listing.status === "ARCHIVED" && (
          <Button variant="secondary" type="button" disabled={pending !== null} onClick={() => void run("restore")}>
            <ArchiveRestore aria-hidden />
            {pending === "restore" ? t("working") : t("restore")}
          </Button>
        )}
      </div>
    </section>
  );
}
