import { requireRole } from "@/features/account/requireSession";

/** Admin sipariş ekranının kapısı (add-shipments D7): oturum yoksa giriş, rol yoksa "bulunamadı". */
export default async function AdminOrdersLayout({ children }: { readonly children: React.ReactNode }) {
  await requireRole("/admin/orders", "ROLE_ADMIN");
  return children;
}
