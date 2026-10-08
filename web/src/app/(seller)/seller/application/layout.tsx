import { requireSignedIn } from "@/features/account/requireSession";

/**
 * Başvuru sayfasının kapısı: yükleniyor sınırından önce çalışır, böylece
 * oturumsuz istek akış başlamadan gerçek bir 307 ile girişe yönlendirilir
 * (AGENTS.md §12.5 "yetkisiz" durumu).
 */
export default async function SellerApplicationLayout({ children }: { readonly children: React.ReactNode }) {
  await requireSignedIn("/seller/application");
  return children;
}
