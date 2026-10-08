import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { FormMessage } from "@/features/account/components/FormMessage";
import { requireRole } from "@/features/account/requireSession";
import { getOwnListing, type SellerListingView } from "@/features/listings/api";
import { ListingActions } from "@/features/listings/components/ListingActions";
import { ListingForm } from "@/features/listings/components/ListingForm";
import { ListingStatusBadge } from "@/features/listings/components/ListingStatusBadge";
import { PhotoManager } from "@/features/listings/components/PhotoManager";
import { isSellerNotApproved, SellerNotApproved } from "@/features/listings/components/SellerNotApproved";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("listings.form.editTitle") };
}

/**
 * İlan düzenleme (product-listings spec'i): yayın eylemleri en üstte, sonra
 * fotoğraflar, sonra bilgiler. Başkasının ilanı veya olmayan
 * ilan "bulunamadı" olur (sahiplik `api`'de sorgu seviyesinde). Admin
 * kaldırdıysa gerekçe en üstte durur; satıcı düzeltebilir ama yayına alamaz
 * (ADR-038 §3).
 */
export default async function EditListingPage({ params }: { readonly params: Promise<{ id: string }> }) {
  const id = Number((await params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    notFound();
  }
  const t = await getTranslations();
  const { accessToken } = await requireRole("/seller/products", "ROLE_SELLER");

  let listing: SellerListingView | null;
  try {
    listing = await getOwnListing(accessToken, id);
  } catch (error) {
    if (isSellerNotApproved(error)) {
      return <SellerNotApproved />;
    }
    throw error;
  }
  if (listing === null) {
    notFound();
  }

  return (
    <main className="mx-auto w-full max-w-xl px-4 pt-8 pb-12 lg:pt-12">
      <Link className={buttonVariants({ variant: "ghost", className: "-ml-3 mb-4" })} href="/seller/products">
        <ArrowLeft aria-hidden />
        {t("listings.form.backToList")}
      </Link>
      <header className="mb-6 flex flex-col gap-3">
        <h1 className="text-2xl font-semibold tracking-tight break-words lg:text-3xl">{listing.name}</h1>
        <div>
          <ListingStatusBadge status={listing.status} />
        </div>
      </header>
      {listing.status === "REMOVED" && (
        <div className="mb-6 flex flex-col gap-1">
          <FormMessage tone="error">
            <strong className="block">{t("listings.form.removedTitle")}</strong>
            {t("listings.form.removedBody", { reason: listing.statusReason ?? "" })}
          </FormMessage>
        </div>
      )}
      <div className="flex flex-col gap-6">
        <Card>
          <CardContent>
            <ListingActions listing={listing} />
          </CardContent>
        </Card>
        <Card>
          <CardContent>
            <PhotoManager listing={listing} />
          </CardContent>
        </Card>
        <Card>
          <CardContent>
            <h2 className="mb-4 text-base font-semibold tracking-tight">{t("listings.form.editTitle")}</h2>
            <ListingForm mode="edit" listing={listing} />
          </CardContent>
        </Card>
      </div>
    </main>
  );
}
