import { requireRole } from "@/features/account/requireSession";

/**
 * Satıcı ilan ekranlarının kapısı (add-product-listings D2): oturum yoksa giriş,
 * satıcı rolü yoksa "bulunamadı". Otorite `api`'dir: rol token'da dursa bile
 * onaylı olmayan satıcı orada 403 alır ve sayfa bunu ayrıca gösterir.
 */
export default async function SellerProductsLayout({ children }: { readonly children: React.ReactNode }) {
  await requireRole("/seller/products", "ROLE_SELLER");
  return children;
}
