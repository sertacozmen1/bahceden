import Link from "next/link";
import type { Metadata } from "next";
import { SearchX } from "lucide-react";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { StatusPanel } from "@/components/StatusPanel";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("notFound.title") };
}

/**
 * Bulunamadı durumu (AGENTS.md §12.5): kullanıcı çıkmaz sokakta bırakılmaz,
 * vitrine dönüş yolu gösterilir. Kökte durur: yalnızca vitrin grubundaki
 * `notFound()` çağrılarını değil, eşleşmeyen her adresi de yakalar.
 */
export default function NotFound() {
  const t = useTranslations();
  return (
    <StatusPanel icon={SearchX} title={t("notFound.title")} body={t("notFound.body")}>
      <Link className={buttonVariants()} href="/">
        {t("notFound.backToStorefront")}
      </Link>
    </StatusPanel>
  );
}
