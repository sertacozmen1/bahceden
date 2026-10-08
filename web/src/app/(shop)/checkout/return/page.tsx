import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { CircleCheck, CircleX, Clock } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { requireSignedIn } from "@/features/account/requireSession";
import { completePayment, type OrderStatus } from "@/features/orders/api";
import { BuyerOrderView } from "@/features/orders/components/BuyerOrderView";
import { cn } from "@/lib/utils";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("checkout.return.title") };
}

const outcomeIcon = {
  PENDING_PAYMENT: Clock,
  PAID: CircleCheck,
  PAYMENT_FAILED: CircleX,
  PAYMENT_EXPIRED: Clock,
  CANCELLED_OUT_OF_STOCK: CircleX,
} as const satisfies Record<OrderStatus, unknown>;

/**
 * Ödeme dönüşü (cart-and-checkout spec'i, D8). Sunucu ödemeyi sağlayıcıdan
 * doğrular ve siparişi sonuçlandırır; çağrı tekrarlanabilir olduğu için sayfa
 * yenilenmesi güvenlidir. Sonuç açıkça söylenir: ödendi, başarısız, süresi
 * doldu ya da stok yetmediği için iade edildi (AGENTS.md §12.5).
 */
export default async function CheckoutReturnPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly order?: string }>;
}) {
  const { order: orderNumber } = await searchParams;
  if (orderNumber === undefined || orderNumber === "") {
    notFound();
  }
  const t = await getTranslations();
  const { accessToken } = await requireSignedIn("/checkout");
  const order = await completePayment(accessToken, orderNumber);
  if (order === null) {
    notFound();
  }
  const Icon = outcomeIcon[order.status];
  const success = order.status === "PAID";

  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
      <header className="mb-6 flex flex-col items-start gap-3">
        <span
          className={cn(
            "grid size-12 place-items-center rounded-full",
            success ? "bg-success/10 text-success" : "bg-muted text-muted-foreground",
          )}
        >
          <Icon aria-hidden className="size-6" />
        </span>
        <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">
          {t("checkout.return.heading", { status: order.status })}
        </h1>
        <p className="max-w-prose text-pretty text-muted-foreground">
          {t("checkout.return.body", { status: order.status, number: order.orderNumber })}
        </p>
        <div className="flex flex-wrap gap-2">
          {order.status === "PENDING_PAYMENT" && (
            <Link className={buttonVariants()} href={`/checkout/return?order=${encodeURIComponent(order.orderNumber)}`}>
              {t("checkout.return.retry")}
            </Link>
          )}
          {!success && order.status !== "PENDING_PAYMENT" && (
            <Link className={buttonVariants()} href="/cart">
              {t("checkout.return.backToCart")}
            </Link>
          )}
          <Link
            className={buttonVariants({ variant: success ? "default" : "outline" })}
            href={`/account/orders/${encodeURIComponent(order.orderNumber)}`}
          >
            {t("checkout.return.viewOrder")}
          </Link>
        </div>
      </header>
      {success && <BuyerOrderView order={order} />}
    </main>
  );
}
