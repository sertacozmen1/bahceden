import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, CircleSlash } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { requireSignedIn } from "@/features/account/requireSession";
import { ClaimForm } from "@/features/claims/components/ClaimForm";
import { getOwnOrder } from "@/features/orders/api";

type Props = {
  readonly params: Promise<{ number: string }>;
  readonly searchParams: Promise<{ readonly sellerOrder?: string }>;
};

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("claims.pageTitle") };
}

/**
 * Ayıplı ürün talebi formu (ADR-081, ADR-086). Sipariş alıcının olmalıdır;
 * değilse "bulunamadı" (sahiplik `api`'de sorgu seviyesinde). Talep açılamıyorsa
 * (süre dolmuş ya da daha önce açılmış) form yerine açıklama gösterilir; son
 * karar yine `api`'nindir.
 */
export default async function ClaimPage({ params, searchParams }: Props) {
  const orderNumber = decodeURIComponent((await params).number);
  const sellerOrderId = Number((await searchParams).sellerOrder);
  const t = await getTranslations();
  const { accessToken } = await requireSignedIn("/account/orders");
  const order = await getOwnOrder(accessToken, orderNumber);
  const sellerOrder = order?.sellerOrders.find((candidate) => candidate.id === sellerOrderId);
  if (order === null || sellerOrder === undefined) {
    notFound();
  }
  const orderHref = `/account/orders/${encodeURIComponent(order.orderNumber)}` as const;

  return (
    <main className="mx-auto w-full max-w-xl px-4 pt-8 pb-12 lg:pt-12">
      <Link className={buttonVariants({ variant: "ghost", className: "-ml-3 mb-4" })} href={orderHref}>
        <ArrowLeft aria-hidden />
        {t("claims.backToOrder")}
      </Link>
      <header className="mb-6 flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{t("claims.pageTitle")}</h1>
        <p className="text-pretty text-muted-foreground">
          {t("claims.pageIntro", { seller: sellerOrder.seller.displayName })}
        </p>
      </header>
      {sellerOrder.claimableUntil ? (
        <ClaimForm orderNumber={order.orderNumber} sellerOrderId={sellerOrder.id} items={sellerOrder.items} />
      ) : (
        <section className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-14 text-center">
          <CircleSlash aria-hidden className="size-6 text-muted-foreground" />
          <h2 className="text-lg font-semibold tracking-tight">{t("claims.unavailableTitle")}</h2>
          <p className="max-w-prose text-pretty text-muted-foreground">{t("claims.unavailableBody")}</p>
        </section>
      )}
    </main>
  );
}
