import { BadgeCheck, CircleAlert, Clock, PauseCircle } from "lucide-react";
import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import type { SellerStatus } from "@/features/seller/api";

/**
 * Satıcı kaydının durumu rozet olarak. Renk tek başına anlam taşımaz
 * (AGENTS.md §12.4): durum her zaman metinle de yazılır, ikon süstür.
 */
const appearance = {
  PENDING: { variant: "info", Icon: Clock },
  APPROVED: { variant: "success", Icon: BadgeCheck },
  REJECTED: { variant: "destructive", Icon: CircleAlert },
  SUSPENDED: { variant: "warning", Icon: PauseCircle },
} as const satisfies Record<SellerStatus, { variant: string; Icon: unknown }>;

export function SellerStatusBadge({ status }: { readonly status: SellerStatus }) {
  const t = useTranslations("seller.application.status");
  const { variant, Icon } = appearance[status];
  return (
    <Badge variant={variant}>
      <Icon aria-hidden />
      {t("label", { status })}
    </Badge>
  );
}
