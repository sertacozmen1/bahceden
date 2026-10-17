import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { getLocale, getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { requireRole } from "@/features/account/requireSession";
import { getClaimForAdmin } from "@/features/claims/api";
import { AdminClaimDecision } from "@/features/claims/components/AdminClaimDecision";
import { ClaimCard } from "@/features/claims/components/ClaimCard";
import { ApiRequestError } from "@/lib/apiError";
import { formatMoney } from "@/lib/money";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("admin.claims.title") };
}

const HTTP_NOT_FOUND = 404;

/**
 * Admin: tek ayıplı ürün talebi (ADR-080, ADR-086). Karar için gereken her şey
 * tek ekranda: fotoğraflar, iki tarafın beyanı, satıcı siparişinin satırları ve
 * satın alınan adetler. Alıcının adı, telefonu ve adresi yoktur (ADR-084).
 * Eylemler yalnızca karar bekleyen talepte görünür; otorite `api`'dir.
 */
export default async function AdminClaimPage({ params }: { readonly params: Promise<{ id: string }> }) {
  const id = Number((await params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    notFound();
  }
  const t = await getTranslations();
  const locale = await getLocale();
  const { accessToken } = await requireRole("/admin/claims", "ROLE_ADMIN");
  const detail = await getClaimForAdmin(accessToken, id).catch((error: unknown) => {
    if (error instanceof ApiRequestError && error.status === HTTP_NOT_FOUND) {
      notFound();
    }
    throw error;
  });

  return (
    <main className="mx-auto w-full max-w-xl px-4 pt-8 pb-12 lg:pt-12">
      <Link className={buttonVariants({ variant: "ghost", className: "-ml-3 mb-4" })} href="/admin/claims">
        <ArrowLeft aria-hidden />
        {t("admin.claims.backToList")}
      </Link>
      <header className="mb-6 flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight tabular-nums lg:text-3xl">{detail.orderNumber}</h1>
        <p className="break-words text-muted-foreground">{detail.sellerName}</p>
      </header>
      <div className="flex flex-col gap-4">
        <ClaimCard claim={detail.claim} viewer="admin" />
        <Card>
          <CardContent className="flex flex-col gap-4">
            <ul className="flex flex-col divide-y divide-border text-sm">
              {detail.items.map((item) => (
                <li key={item.id} className="flex justify-between gap-3 py-2 first:pt-0 last:pb-0">
                  <span className="min-w-0 break-words">
                    <span className="font-medium">{item.productName}</span>{" "}
                    <span className="text-muted-foreground tabular-nums">× {item.quantity}</span>
                  </span>
                  <span className="shrink-0 tabular-nums">{formatMoney(item.lineTotal, locale)}</span>
                </li>
              ))}
            </ul>
            <dl className="flex flex-col gap-2 border-t border-border pt-3 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">{t("orders.itemsTotal")}</dt>
                <dd className="tabular-nums">{formatMoney(detail.itemsTotal, locale)}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">{t("orders.shipping")}</dt>
                <dd className="tabular-nums">{formatMoney(detail.shippingFee, locale)}</dd>
              </div>
            </dl>
          </CardContent>
        </Card>
        {detail.decidable && (
          <section className="flex flex-col gap-3">
            <h2 className="font-semibold tracking-tight">{t("admin.claims.decisionTitle")}</h2>
            <p className="text-sm text-pretty text-muted-foreground">{t("admin.claims.decisionIntro")}</p>
            <AdminClaimDecision detail={detail} />
          </section>
        )}
      </div>
    </main>
  );
}
