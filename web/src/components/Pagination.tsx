import type { Route } from "next";
import Link from "next/link";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Sayfalı listelerin önceki / sonraki gezintisi (AGENTS.md §11.5 ortak liste
 * şekli). Vitrin ve admin satıcı listesi aynı bileşeni kullanır (K9).
 *
 * Sayfa adresi sorgu parametresidir (`?page=`); diğer sorgu parametreleri
 * (örn. durum süzgeci) korunur. Tek sayfalık listede hiçbir şey çizilmez.
 */
type PaginationProps = {
  /** Sıfırdan başlayan geçerli sayfa. */
  readonly page: number;
  readonly totalPages: number;
  readonly pathname: Route;
  readonly query?: Readonly<Record<string, string>>;
};

export function Pagination({ page, totalPages, pathname, query = {} }: PaginationProps) {
  const t = useTranslations("pagination");
  if (totalPages <= 1) {
    return null;
  }
  const current = t("current", { page: page + 1, totalPages });
  return (
    <nav className="mt-8 flex items-center justify-between gap-3" aria-label={current}>
      {page > 0 ? (
        <Link className={buttonVariants({ variant: "outline" })} href={{ pathname, query: { ...query, page: page - 1 } }}>
          <ChevronLeft aria-hidden />
          {t("previous")}
        </Link>
      ) : (
        <span className={cn(buttonVariants({ variant: "outline" }), "opacity-50")} aria-disabled="true">
          <ChevronLeft aria-hidden />
          {t("previous")}
        </span>
      )}

      <span className="text-sm text-muted-foreground tabular-nums">{current}</span>

      {page + 1 < totalPages ? (
        <Link className={buttonVariants({ variant: "outline" })} href={{ pathname, query: { ...query, page: page + 1 } }}>
          {t("next")}
          <ChevronRight aria-hidden />
        </Link>
      ) : (
        <span className={cn(buttonVariants({ variant: "outline" }), "opacity-50")} aria-disabled="true">
          {t("next")}
          <ChevronRight aria-hidden />
        </span>
      )}
    </nav>
  );
}

/**
 * Sayfa numarası istemciden gelir: geçersiz veya negatif değer ilk sayfaya
 * düşer. Otorite sunucudadır, ama kullanıcıya gereksiz hata göstermeyiz.
 */
export function parsePage(value: string | undefined): number {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
}
