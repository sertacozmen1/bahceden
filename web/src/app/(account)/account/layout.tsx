import { requireSignedIn } from "@/features/account/requireSession";

/**
 * Hesabım kapısı: yükleniyor sınırından önce çalışır, böylece oturumsuz
 * istek akış başlamadan gerçek bir 307 ile yönlendirilir.
 */
export default async function MyAccountLayout({ children }: { readonly children: React.ReactNode }) {
  await requireSignedIn("/account");
  return children;
}
