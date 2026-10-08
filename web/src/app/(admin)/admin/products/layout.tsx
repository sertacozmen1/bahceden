import { requireRole } from "@/features/account/requireSession";

/** Admin ilan ekranının kapısı (add-product-listings D7): oturum yoksa giriş, rol yoksa "bulunamadı". */
export default async function AdminProductsLayout({ children }: { readonly children: React.ReactNode }) {
  await requireRole("/admin/products", "ROLE_ADMIN");
  return children;
}
