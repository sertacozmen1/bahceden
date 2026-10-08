import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, FileText } from "lucide-react";
import { getLocale, getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { requireRole } from "@/features/account/requireSession";
import { getSellerForAdmin } from "@/features/admin/api";
import { adminBffPaths } from "@/features/admin/bff";
import { SellerDecisionActions } from "@/features/admin/components/SellerDecisionActions";
import { SellerStatusBadge } from "@/features/seller/components/SellerStatusBadge";
import { ApiRequestError } from "@/lib/apiError";
import { formatDateTime } from "@/lib/datetime";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("admin.sellers.title") };
}

const HTTP_NOT_FOUND = 404;

/**
 * Admin: tek satıcı kaydı (seller-onboarding spec'i "Admin başvuruları karara
 * bağlar", "Kararlar bildirilir ve denetim izi saklanır").
 *
 * Karar için gereken her şey tek ekranda: beyan, iletişim, belge, geçmiş ve
 * yalnızca şimdiki durumdan yapılabilen eylemler. Belge yeni sekmede, BFF
 * üzerinden açılır; herkese açık adresi yoktur (D6).
 */
export default async function AdminSellerPage({ params }: { readonly params: Promise<{ id: string }> }) {
  const { id: rawId } = await params;
  const id = Number(rawId);
  if (!Number.isSafeInteger(id) || id <= 0) {
    notFound();
  }
  const t = await getTranslations();
  const locale = await getLocale();
  const { accessToken } = await requireRole("/admin/sellers", "ROLE_ADMIN");
  const seller = await getSellerForAdmin(accessToken, id).catch((error: unknown) => {
    if (error instanceof ApiRequestError && error.status === HTTP_NOT_FOUND) {
      notFound();
    }
    throw error;
  });
  const none = t("admin.sellers.detail.none");

  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
      <Link className={buttonVariants({ variant: "ghost", className: "-ml-3 mb-4" })} href="/admin/sellers">
        <ArrowLeft aria-hidden />
        {t("admin.sellers.backToList")}
      </Link>
      <header className="mb-6 flex flex-col gap-3">
        <h1 className="text-2xl font-semibold tracking-tight break-words lg:text-3xl">{seller.displayName}</h1>
        <div>
          <SellerStatusBadge status={seller.status} />
        </div>
      </header>

      <Card>
        <CardContent className="flex flex-col gap-5">
          <dl className="grid gap-4 sm:grid-cols-2">
            <Detail label={t("admin.sellers.detail.accountEmail")} value={seller.accountEmail ?? none} breakAll />
            <Detail label={t("admin.sellers.detail.phone")} value={seller.phone ?? none} />
            <Detail label={t("admin.sellers.detail.city")} value={seller.city} />
            <Detail
              label={t("admin.sellers.detail.appliedAt")}
              value={formatDateTime(seller.createdAt, locale)}
            />
            <Detail
              label={t("admin.sellers.detail.documentType")}
              value={
                seller.documentType === undefined || seller.documentType === null
                  ? none
                  : t("seller.application.documentType", { type: seller.documentType })
              }
            />
            {seller.statusReason !== undefined && seller.statusReason !== null && (
              <Detail label={t("admin.sellers.detail.reason")} value={seller.statusReason} />
            )}
          </dl>
          {seller.documentType === undefined || seller.documentType === null ? (
            <p className="text-sm text-muted-foreground">{t("admin.sellers.detail.noDocument")}</p>
          ) : (
            <a
              className={buttonVariants({ variant: "outline", className: "self-start" })}
              href={adminBffPaths.document(seller.id)}
              target="_blank"
              rel="noopener"
            >
              <FileText aria-hidden />
              {t("admin.sellers.detail.openDocument")}
            </a>
          )}
          {seller.status !== "REJECTED" && (
            <>
              <Separator />
              <section aria-labelledby="decision-title" className="flex flex-col gap-3">
                <h2 id="decision-title" className="text-base font-semibold tracking-tight">
                  {t("admin.sellers.detail.actionsTitle")}
                </h2>
                <SellerDecisionActions sellerId={seller.id} status={seller.status} />
              </section>
            </>
          )}
        </CardContent>
      </Card>

      {seller.history.length > 0 && (
        <section aria-labelledby="history-title" className="mt-8 flex flex-col gap-3">
          <h2 id="history-title" className="text-lg font-semibold tracking-tight">
            {t("admin.sellers.detail.historyTitle")}
          </h2>
          <ol className="flex flex-col gap-3 border-l border-border pl-4">
            {seller.history.map((change, index) => (
              <li key={`${change.createdAt}-${index}`} className="flex flex-col gap-1">
                <span className="font-medium">{t("admin.sellers.detail.historyItem", { to: change.toStatus })}</span>
                <span className="text-sm text-muted-foreground break-all">
                  {t("admin.sellers.detail.historyBy", {
                    email: change.actorEmail,
                    date: formatDateTime(change.createdAt, locale),
                  })}
                </span>
                {change.reason !== undefined && change.reason !== null && (
                  <span className="text-sm text-pretty">{change.reason}</span>
                )}
              </li>
            ))}
          </ol>
        </section>
      )}
    </main>
  );
}

function Detail({ label, value, breakAll }: { readonly label: string; readonly value: string; readonly breakAll?: boolean }) {
  return (
    <div className="grid gap-1">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className={breakAll === true ? "font-medium break-all" : "font-medium break-words"}>{value}</dd>
    </div>
  );
}
