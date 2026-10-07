import Link from "next/link";
import type { Metadata } from "next";
import { SearchX } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { StatusPanel } from "@/components/StatusPanel";
import { texts } from "@/lib/texts";

/**
 * Bulunamadı durumu (AGENTS.md §12.5): kullanıcı çıkmaz sokakta bırakılmaz,
 * vitrine dönüş yolu gösterilir. Kökte durur: yalnızca vitrin grubundaki
 * `notFound()` çağrılarını değil, eşleşmeyen her adresi de yakalar.
 */
export const metadata: Metadata = {
  title: texts.notFound.title,
};

export default function NotFound() {
  return (
    <StatusPanel icon={SearchX} title={texts.notFound.title} body={texts.notFound.body}>
      <Link className={buttonVariants()} href="/">
        {texts.notFound.backToStorefront}
      </Link>
    </StatusPanel>
  );
}
