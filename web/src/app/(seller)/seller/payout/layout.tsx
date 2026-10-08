import { requireRole } from "@/features/account/requireSession";

/**
 * Satıcının ödeme bilgileri ekranının kapısı (add-cart-and-checkout D2, D10):
 * oturum yoksa giriş, satıcı rolü yoksa "bulunamadı". Otorite `api`'dir.
 */
export default async function SellerPayoutLayout({ children }: { readonly children: React.ReactNode }) {
  await requireRole("/seller/payout", "ROLE_SELLER");
  return children;
}
