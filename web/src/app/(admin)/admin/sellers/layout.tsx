import { requireRole } from "@/features/account/requireSession";

/**
 * Admin satıcı ekranlarının kapısı (add-seller-onboarding D7): oturum yoksa
 * giriş, rol yoksa "bulunamadı". Yükleniyor sınırından önce çalışır, böylece
 * yönlendirme gerçek bir HTTP yanıtıdır. Otorite yine `api`'dir.
 */
export default async function AdminSellersLayout({ children }: { readonly children: React.ReactNode }) {
  await requireRole("/admin/sellers", "ROLE_ADMIN");
  return children;
}
