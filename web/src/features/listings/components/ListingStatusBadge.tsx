import { Archive, Ban, CircleDot, PencilLine } from "lucide-react";
import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import type { ListingStatus } from "@/features/listings/api";

/**
 * İlan durumu rozeti. Renk tek başına anlam taşımaz (AGENTS.md §12.4): durum
 * her zaman metinle yazılır, ikon süstür.
 */
const appearance = {
  DRAFT: { variant: "secondary", Icon: PencilLine },
  PUBLISHED: { variant: "success", Icon: CircleDot },
  ARCHIVED: { variant: "outline", Icon: Archive },
  REMOVED: { variant: "destructive", Icon: Ban },
} as const satisfies Record<ListingStatus, { variant: string; Icon: unknown }>;

export function ListingStatusBadge({ status }: { readonly status: ListingStatus }) {
  const t = useTranslations("listings");
  const { variant, Icon } = appearance[status];
  return (
    <Badge variant={variant}>
      <Icon aria-hidden />
      {t("status", { status })}
    </Badge>
  );
}
