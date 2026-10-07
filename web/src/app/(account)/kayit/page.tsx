import type { Metadata } from "next";
import Link from "next/link";
import { RegisterForm } from "@/features/account/components/RegisterForm";
import { texts } from "@/lib/texts";
import styles from "../account.module.css";

export const metadata: Metadata = { title: texts.account.register.title };

/** Kayıt sayfası (account-identity spec'i "E-posta ve parola ile hesap açma"). */
export default function RegisterPage() {
  return (
    <main className={styles.page}>
      <h1 className={styles.title}>{texts.account.register.title}</h1>
      <p className={styles.intro}>{texts.account.register.intro}</p>
      <div className={styles.panel}>
        <RegisterForm />
      </div>
      <p className={styles.aside}>
        {texts.account.register.haveAccount} <Link href="/giris">{texts.account.nav.signIn}</Link>
      </p>
    </main>
  );
}
