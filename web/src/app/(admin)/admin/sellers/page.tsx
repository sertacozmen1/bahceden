import type { Metadata } from "next";
import Link from "next/link";
import { Inbox } from "lucide-react";
import { getLocale, getTranslations } from "next-intl/server";
import { Pagination, parsePage } from "@/components/Pagination";
import { Card, CardContent } from "@/components/ui/card";
import { requireRole } from "@/features/account/requireSession";
import { adminSellerStatuses, listSellersForAdmin } from "@/features/admin/api";
import { AdminNav } from "@/features/admin/components/AdminNav";
import { SellerStatusBadge } from "@/features/seller/components/SellerStatusBadge";
import { formatDateTime } from "@/lib/datetime";
import { cn } from "@/lib/utils";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("admin.sellers.title") };
}

const PAGE_SIZE = 20;

type SearchParams = { readonly status?: string; readonly page?: string };

/**
 * Admin: satıcı kayıtları (seller-onboarding spec'i "Admin başvuruları karara
 * bağlar"). Varsayılan sekme bekleyen başvurulardır; sekme ve sayfa adreste
 * durur, böylece geri tuşu ve yenileme aynı listeyi gösterir.
 *
 * Satırlarda kişisel veri yoktur (telefon, e-posta); onlar yalnızca detayda.
 */
export default async function AdminSellersPage({ searchParams }: { readonly searchParams: Promise<SearchParams> }) {
  const { status: rawStatus, page: rawPage } = await searchParams;
  const status = adminSellerStatuses.find((candidate) => candidate === rawStatus) ?? "PENDING";
  const t = await getTranslations();
  const locale = await getLocale();
  const { accessToken } = await requireRole("/admin/sellers", "ROLE_ADMIN");
  const result = await listSellersForAdmin(accessToken, status, parsePage(rawPage), PAGE_SIZE);

  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
      <AdminNav current="sellers" />
      <header className="mb-6 flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{t("admin.sellers.title")}</h1>
        <p className="text-pretty text-muted-foreground">{t("admin.sellers.intro")}</p>
      </header>

      <nav aria-label={t("admin.sellers.tabsLabel")} className="mb-5 -mx-4 overflow-x-auto px-4">
        <ul className="flex gap-2">
          {adminSellerStatuses.map((candidate) => (
            <li key={candidate}>
              <Link
                href={{ pathname: "/admin/sellers", query: { status: candidate } }}
                aria-current={candidate === status ? "page" : undefined}
                className={cn(
                  "inline-flex h-11 items-center rounded-lg border px-4 text-sm font-medium whitespace-nowrap lg:h-10",
                  candidate === status
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border bg-card hover:bg-muted",
                )}
              >
                {t("admin.sellers.tab", { status: candidate })}
              </Link>
            </li>
          ))}
        </ul>
      </nav>

      {result.items.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-start gap-3">
            <Inbox aria-hidden className="size-6 text-muted-foreground" />
            <h2 className="text-lg font-semibold tracking-tight">{t("admin.sellers.emptyTitle")}</h2>
            <p className="text-pretty text-muted-foreground">{t("admin.sellers.emptyBody")}</p>
          </CardContent>
        </Card>
      ) : (
        <ul className="flex flex-col gap-3">
          {result.items.map((seller) => (
            <li key={seller.id}>
              <Link
                href={`/admin/sellers/${seller.id}`}
                className="flex flex-col gap-2 rounded-xl border border-border bg-card p-4 hover:bg-muted sm:flex-row sm:items-center sm:justify-between"
              >
                <span className="flex flex-col gap-1">
                  <span className="font-medium break-words">{seller.displayName}</span>
                  <span className="text-sm text-muted-foreground">
                    {seller.city} · {t("admin.sellers.appliedAt", { date: formatDateTime(seller.createdAt, locale) })}
                  </span>
                </span>
                <SellerStatusBadge status={seller.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}

      <Pagination page={result.page} totalPages={result.totalPages} pathname="/admin/sellers" query={{ status }} />
    </main>
  );
}
