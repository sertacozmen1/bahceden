import type { Metadata } from "next";
import { RefreshCw } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { StatusPanel } from "@/components/StatusPanel";
import { bffPaths } from "@/features/account/bff";
import { SessionRefreshRedirect } from "@/features/account/components/SessionRefreshRedirect";
import { safeReturnPath } from "@/lib/sameOrigin";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("account.sessionRefresh.title") };
}

/**
 * Oturum yenileme ara sayfası (ADR-023). Erişim token'ı dolmuşken uygulama içi
 * bir bağlantıyla korunan sayfaya gidilince kapı buraya yönlendirir; sayfa
 * yenileme ucuna tam sayfa gezintisiyle geçer ve kullanıcı geldiği sayfaya
 * döner. JavaScript çalışmazsa bağlantı aynı işi yapar.
 */
export default async function SessionRefreshPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly next?: string }>;
}) {
  const { next } = await searchParams;
  const t = await getTranslations();
  const href = `${bffPaths.refresh}?next=${encodeURIComponent(safeReturnPath(next, "/account"))}`;
  return (
    <>
      <SessionRefreshRedirect href={href} />
      <StatusPanel
        icon={RefreshCw}
        title={t("account.sessionRefresh.title")}
        body={t("account.sessionRefresh.body")}
      >
        <a className={buttonVariants({ variant: "outline" })} href={href}>
          {t("account.sessionRefresh.continue")}
        </a>
      </StatusPanel>
    </>
  );
}
