import type { Metadata } from "next";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { requireSignedIn } from "@/features/account/requireSession";
import { listAddresses, listDeliveryProvinces } from "@/features/addresses/api";
import { AddressBook } from "@/features/addresses/components/AddressBook";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("addresses.title") };
}

/**
 * Adreslerim (cart-and-checkout spec'i "Teslimat adresi", D5). Kapı üst
 * `layout.tsx`'tedir; adresler her istekte taze okunur. Teslimat yapılan
 * iller formda seçenek olarak durur.
 */
export default async function AddressesPage() {
  const t = await getTranslations();
  const { accessToken } = await requireSignedIn("/account/addresses");
  const [addresses, provinces] = await Promise.all([listAddresses(accessToken), listDeliveryProvinces()]);
  return (
    <main className="mx-auto w-full max-w-xl px-4 pt-8 pb-12 lg:pt-12">
      <Link className={buttonVariants({ variant: "ghost", className: "-ml-3 mb-4" })} href="/account">
        <ArrowLeft aria-hidden />
        {t("addresses.back")}
      </Link>
      <header className="mb-6 flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{t("addresses.title")}</h1>
        <p className="max-w-prose text-pretty text-muted-foreground">
          {t("addresses.intro", { provinces: provinces.map((province) => province.name).join(", ") })}
        </p>
      </header>
      <AddressBook addresses={addresses} provinces={provinces} />
    </main>
  );
}
