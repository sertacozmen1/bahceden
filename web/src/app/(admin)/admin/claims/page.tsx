import type { Metadata } from "next";
import Link from "next/link";
import { Inbox } from "lucide-react";
import { getLocale, getTranslations } from "next-intl/server";
import { FilterTabs } from "@/components/FilterTabs";
import { Pagination, parsePage } from "@/components/Pagination";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { requireRole } from "@/features/account/requireSession";
import { AdminNav } from "@/features/admin/components/AdminNav";
import { adminClaimStatuses, listClaimsForAdmin } from "@/features/claims/api";
import { formatDateTime } from "@/lib/datetime";
import { formatMoney } from "@/lib/money";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("admin.claims.title") };
}

const PAGE_SIZE = 20;

/**
 * Admin: ayıplı ürün talepleri (defect-claims spec'i "Admin yönetime taşınan
 * talebi karara bağlar", ADR-086). Varsayılan sekme karar bekleyenlerdir;
 * diğer durumlar izlemek için ayrı sekmelerdedir. Liste alıcının kişisel
 * verisini taşımaz (ADR-084).
 */
export default async function AdminClaimsPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly status?: string; readonly page?: string }>;
}) {
  const { status: rawStatus, page } = await searchParams;
  const status = adminClaimStatuses.find((candidate) => candidate === rawStatus) ?? "ESCALATED";
  const t = await getTranslations();
  const locale = await getLocale();
  const { accessToken } = await requireRole("/admin/claims", "ROLE_ADMIN");
  const result = await listClaimsForAdmin(accessToken, status, parsePage(page), PAGE_SIZE);

  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
      <AdminNav current="claims" />
      <header className="mb-6 flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{t("admin.claims.title")}</h1>
        <p className="text-pretty text-muted-foreground">{t("admin.claims.intro")}</p>
      </header>

      <FilterTabs
        label={t("admin.claims.tabsLabel")}
        pathname="/admin/claims"
        current={status}
        options={adminClaimStatuses.map((candidate) => ({
          value: candidate,
          label: t("claims.status", { status: candidate }),
        }))}
      />

      {result.items.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-start gap-3">
            <Inbox aria-hidden className="size-6 text-muted-foreground" />
            <h2 className="text-lg font-semibold tracking-tight">{t("admin.claims.emptyTitle")}</h2>
            <p className="text-pretty text-muted-foreground">{t("admin.claims.emptyBody")}</p>
          </CardContent>
        </Card>
      ) : (
        <ul className="flex flex-col gap-3">
          {result.items.map((claim) => (
            <li key={claim.id}>
              <Link
                href={`/admin/claims/${claim.id}`}
                className="flex flex-col gap-2 rounded-xl border border-border bg-card p-4 hover:bg-muted sm:flex-row sm:items-center sm:justify-between"
              >
                <span className="flex flex-col gap-1">
                  <span className="font-medium tabular-nums">{claim.orderNumber}</span>
                  <span className="text-sm text-muted-foreground break-words">
                    {claim.sellerName} · {formatDateTime(claim.createdAt, locale)}
                  </span>
                  {claim.refundAmount && (
                    <span className="text-sm text-muted-foreground tabular-nums">
                      {t("admin.claims.refunded", { amount: formatMoney(claim.refundAmount, locale) })}
                    </span>
                  )}
                </span>
                <Badge variant={claim.status === "ESCALATED" ? "warning" : "secondary"}>
                  {t("claims.status", { status: claim.status })}
                </Badge>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <Pagination page={result.page} totalPages={result.totalPages} pathname="/admin/claims" query={{ status }} />
    </main>
  );
}
