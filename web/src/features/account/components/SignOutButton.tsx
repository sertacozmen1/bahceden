"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { bffPaths } from "@/features/account/bff";
import { postToBff } from "@/lib/bffClient";
import { texts } from "@/lib/texts";

/**
 * Çıkış düğmesi. BFF oturum zincirini `api`'de iptal eder ve çerezleri siler;
 * ardından vitrine dönülür ve sunucu bileşenleri yenilenir. BFF'e ulaşılamasa bile
 * kullanıcı vitrine gönderilir: düzen oturum durumunu sunucuda yeniden okur.
 */
export function SignOutButton({ className }: { readonly className?: string }) {
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
    <button className={className} type="button" onClick={signOut} disabled={pending}>
      {pending ? texts.account.nav.signingOut : texts.account.nav.signOut}
    </button>
  );
}
