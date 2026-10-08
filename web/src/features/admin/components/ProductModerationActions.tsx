"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Ban, RotateCcw } from "lucide-react";
import { useTranslations } from "next-intl";
import { ReasonDialog } from "@/components/ReasonDialog";
import { Button } from "@/components/ui/button";
import { FormMessage } from "@/features/account/components/FormMessage";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import type { AdminProductSummary } from "@/features/admin/api";
import { adminBffPaths } from "@/features/admin/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * İlan denetim eylemi (product-listings spec'i "Admin ilanı kaldırır ve yeniden
 * açar"). Yayındaki ilan gerekçe diyaloğuyla kaldırılır (yıkıcı); kaldırılmış
 * ilan tek düğmeyle yeniden açılır. Otorite `api`'dir.
 */
export function ProductModerationActions({ product }: { readonly product: AdminProductSummary }) {
  const t = useTranslations("admin.products");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (product.status === "PUBLISHED") {
    return (
      <ReasonDialog
        id={`product-${product.id}`}
        icon={Ban}
        triggerLabel={t("remove")}
        title={t("removeTitle")}
        description={t("removeBody")}
        confirmLabel={t("confirmRemove")}
        onSubmit={async (reason) => {
          await postToBff(adminBffPaths.productDecision(product.id, "remove"), { reason });
          router.refresh();
        }}
      />
    );
  }
  if (product.status !== "REMOVED") {
    return null;
  }

  async function reinstate() {
    setPending(true);
    setError(null);
    try {
      await postToBff(adminBffPaths.productDecision(product.id, "reinstate"));
      router.refresh();
    } catch (cause) {
      setError(toFormErrors(cause, errorTexts).general ?? errorTexts.unavailable);
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {error !== null && <FormMessage tone="error">{error}</FormMessage>}
      <Button variant="secondary" type="button" className="self-start" disabled={pending} onClick={() => void reinstate()}>
        <RotateCcw aria-hidden />
        {pending ? t("reinstating") : t("reinstate")}
      </Button>
    </div>
  );
}
