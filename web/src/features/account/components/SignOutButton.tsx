"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { LogOut } from "lucide-react";
import type { VariantProps } from "class-variance-authority";
import { useTranslations } from "next-intl";
import { Button, type buttonVariants } from "@/components/ui/button";
import { bffPaths } from "@/features/account/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * Çıkış düğmesi. BFF oturum zincirini `api`'de iptal eder ve çerezleri siler;
 * ardından vitrine dönülür ve sunucu bileşenleri yenilenir. BFF'e ulaşılamasa bile
 * kullanıcı vitrine gönderilir: düzen oturum durumunu sunucuda yeniden okur.
 *
 * Yıkıcı bir eylem değildir (veri silmez), bu yüzden kırmızı değil nötr görünür.
 *
 * `compact`: dar ekranda yalnızca ikon görünür, erişilebilir ad korunur
 * (başlıkta yer kalsın diye; 375 px'de yatay kaydırma olmaz).
 */
type SignOutButtonProps = VariantProps<typeof buttonVariants> & {
  readonly className?: string;
  readonly compact?: boolean;
};

export function SignOutButton({ variant = "outline", size, className, compact = false }: SignOutButtonProps) {
  const t = useTranslations();
  const router = useRouter();
  const [pending, setPending] = useState(false);

  const label = pending ? t("account.nav.signingOut") : t("account.nav.signOut");

  function signOut() {
    setPending(true);
    const goToStorefront = () => {
      router.push("/");
      router.refresh();
    };
    // Başarı da başarısızlık da aynı yere gider: oturum durumu sunucuda yeniden okunur.
    void postToBff(bffPaths.logout).then(goToStorefront, goToStorefront);
  }

  return (
    <Button
      variant={variant}
      size={size}
      className={className}
      type="button"
      onClick={signOut}
      disabled={pending}
      aria-label={compact ? label : undefined}
    >
      <LogOut aria-hidden />
      {compact ? <span className="hidden sm:inline">{label}</span> : label}
    </Button>
  );
}
