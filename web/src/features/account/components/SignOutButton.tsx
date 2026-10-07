"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { LogOut } from "lucide-react";
import type { VariantProps } from "class-variance-authority";
import { Button, type buttonVariants } from "@/components/ui/button";
import { bffPaths } from "@/features/account/bff";
import { postToBff } from "@/lib/bffClient";
import { texts } from "@/lib/texts";

/**
 * Çıkış düğmesi. BFF oturum zincirini `api`'de iptal eder ve çerezleri siler;
 * ardından vitrine dönülür ve sunucu bileşenleri yenilenir. BFF'e ulaşılamasa bile
 * kullanıcı vitrine gönderilir: düzen oturum durumunu sunucuda yeniden okur.
 *
 * Yıkıcı bir eylem değildir (veri silmez), bu yüzden kırmızı değil nötr görünür.
 */
type SignOutButtonProps = VariantProps<typeof buttonVariants> & { readonly className?: string };

export function SignOutButton({ variant = "outline", size, className }: SignOutButtonProps) {
  const router = useRouter();
  const [pending, setPending] = useState(false);

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
    <Button variant={variant} size={size} className={className} type="button" onClick={signOut} disabled={pending}>
      <LogOut aria-hidden />
      {pending ? texts.account.nav.signingOut : texts.account.nav.signOut}
    </Button>
  );
}
