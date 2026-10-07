import type { Metadata } from "next";
import Link from "next/link";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { RegisterForm } from "@/features/account/components/RegisterForm";
import { texts } from "@/lib/texts";

export const metadata: Metadata = { title: texts.account.register.title };

/** Kayıt sayfası (account-identity spec'i "E-posta ve parola ile hesap açma"). */
export default function RegisterPage() {
  return (
    <AccountScreen
      title={texts.account.register.title}
      intro={texts.account.register.intro}
      aside={
        <>
          {texts.account.register.haveAccount}{" "}
          <Link className="font-medium text-primary underline-offset-4 hover:underline" href="/giris">
            {texts.account.nav.signIn}
          </Link>
        </>
      }
    >
      <RegisterForm />
    </AccountScreen>
  );
}
