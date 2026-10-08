import Link from "next/link";
import { CircleUserRound, ShieldCheck, Store } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { currentSession, type SessionState } from "@/features/account/api";
import { SignOutButton } from "@/features/account/components/SignOutButton";
import { maskEmail } from "@/features/account/maskEmail";
import { cn } from "@/lib/utils";

/**
 * Üst başlıktaki oturum alanı (tasks 4.6).
 *
 * Oturum durumu istek başına bir kez okunur (`currentSession` önbelleği) ve
 * sayfa aynı sonucu paylaşır. `api`'ye ulaşılamazsa başlık vitrini düşürmez:
 * oturum bilgisi gerektirmeyen nötr bir "Hesabım" bağlantısı gösterilir,
 * asıl hata ilgili sayfanın kendi hata durumunda görünür (AGENTS.md §12.5).
 *
 * Dar ekranda maskeli e-posta gizlenir, ikon kalır; erişilebilir ad
 * `aria-label` ile korunur.
 *
 * "Ürünlerim" yalnızca `ROLE_SELLER`, yönetim bağlantısı yalnızca `ROLE_ADMIN` taşıyan hesapta görünür. Bu bir
 * kolaylıktır, yetkilendirme değil: otorite `api`'dir (add-seller-onboarding D7).
 */
const navClassName = "flex items-center gap-1 sm:gap-2";

export async function SessionNav() {
  const t = await getTranslations();
  const session = await readSession();

  if (session?.kind === "signed-in") {
    const masked = maskEmail(session.account.email);
    return (
      <nav className={navClassName} aria-label={t("account.nav.signedInAs", { email: masked })}>
        {session.account.roles.includes("ROLE_SELLER") && (
          <Link className={buttonVariants({ variant: "ghost" })} href="/seller/products" aria-label={t("listings.nav")}>
            <Store aria-hidden />
            <span className="hidden lg:inline">{t("listings.nav")}</span>
          </Link>
        )}
        {session.account.roles.includes("ROLE_ADMIN") && (
          <Link className={buttonVariants({ variant: "ghost" })} href="/admin/sellers" aria-label={t("admin.nav")}>
            <ShieldCheck aria-hidden />
            <span className="hidden lg:inline">{t("admin.nav")}</span>
          </Link>
        )}
        <Link className={buttonVariants({ variant: "ghost" })} href="/account" aria-label={masked}>
          <CircleUserRound aria-hidden />
          <span className="hidden max-w-52 truncate sm:inline">{masked}</span>
        </Link>
        <SignOutButton variant="outline" />
      </nav>
    );
  }

  if (session === null || session.kind === "expired") {
    // Oturum var ama yenilenmeli ya da durum okunamadı: "Hesabım" yenilemeyi tetikler.
    return (
      <nav className={navClassName} aria-label={t("account.nav.myAccount")}>
        <Link className={buttonVariants({ variant: "outline" })} href="/account">
          <CircleUserRound aria-hidden />
          {t("account.nav.myAccount")}
        </Link>
      </nav>
    );
  }

  return (
    <nav className={navClassName} aria-label={t("account.nav.signIn")}>
      <Link className={cn(buttonVariants({ variant: "ghost" }), "max-sm:px-2.5")} href="/login">
        {t("account.nav.signIn")}
      </Link>
      <Link className={cn(buttonVariants(), "max-sm:px-3")} href="/register">
        {t("account.nav.register")}
      </Link>
    </nav>
  );
}

/** Oturum durumunu okur; `api`'ye ulaşılamazsa `null` (başlık nötr kalır). */
async function readSession(): Promise<SessionState | null> {
  try {
    return await currentSession();
  } catch {
    return null;
  }
}
