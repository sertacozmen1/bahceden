import type { Metadata } from "next";
import Link from "next/link";
import { headers } from "next/headers";
import { verifyEmail } from "@/features/account/api";
import { ApiRequestError } from "@/lib/apiError";
import { texts } from "@/lib/texts";
import styles from "../account.module.css";

export const metadata: Metadata = { title: texts.account.verification.title };

/**
 * E-postadaki doğrulama bağlantısının indiği sayfa (design.md D3, D9).
 *
 * Token sunucu tarafında tüketilir. Sonuç girişi etkilemez (ADR-017):
 * başarıda da başarısızlıkta da kullanıcı yoluna devam edebilir. `api`
 * reddederse sunucunun mesajı gösterilir ve yeni bağlantının nereden
 * isteneceği söylenir. `api`'ye ulaşılamazsa istisna yukarı çıkar ve hata
 * durumu (`error.tsx`) yeniden denemeyi sunar.
 */
export default async function VerificationPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly token?: string }>;
}) {
  const { token } = await searchParams;
  const failure = await consume(token);

  return (
    <main className={styles.page}>
      <section className={styles.panel} role={failure === null ? "status" : "alert"}>
        <h1 className={styles.title}>
          {failure === null ? texts.account.verification.successTitle : texts.account.verification.failedTitle}
        </h1>
        <p className={styles.intro}>{failure ?? texts.account.verification.successBody}</p>
        {failure !== null && <p>{texts.account.verification.requestNewLink}</p>}
        <Link className={styles.action} href="/hesabim">
          {texts.account.verification.goToAccount}
        </Link>
        <Link href="/">{texts.account.verification.goToStorefront}</Link>
      </section>
    </main>
  );
}

/** Başarıda `null`, reddedilirse kullanıcıya gösterilecek mesaj. */
async function consume(token: string | undefined): Promise<string | null> {
  if (token === undefined || token.trim() === "") {
    return texts.account.verification.missingToken;
  }
  try {
    await verifyEmail(token, (await headers()).get("x-forwarded-for"));
    return null;
  } catch (error) {
    if (error instanceof ApiRequestError) {
      return error.message;
    }
    throw error;
  }
}
