import { requireRole } from "@/features/account/requireSession";

/**
 * Gelen siparişler ekranlarının kapısı (add-cart-and-checkout D10): oturum
 * yoksa giriş, satıcı rolü yoksa "bulunamadı". Otorite `api`'dir.
 */
export default async function SellerOrdersLayout({ children }: { readonly children: React.ReactNode }) {
  await requireRole("/seller/orders", "ROLE_SELLER");
  return children;
}
