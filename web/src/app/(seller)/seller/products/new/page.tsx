import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { ListingForm } from "@/features/listings/components/ListingForm";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("listings.form.newTitle") };
}

/**
 * Yeni ilan: bilgiler girilir ve taslak kaydedilir; fotoğraf ve yayın
 * düzenleme ekranında yapılır (D3). Kapı `layout.tsx`'te; onaylı olmayan
 * satıcının gönderimi `api`'den 403 olarak formda görünür.
 */
export default async function NewListingPage() {
  const t = await getTranslations();
  return (
    <AccountScreen title={t("listings.form.newTitle")} intro={t("listings.form.newIntro")} className="max-w-xl">
      <ListingForm mode="create" />
    </AccountScreen>
  );
}
