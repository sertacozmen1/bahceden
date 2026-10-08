import { requireSignedIn } from "@/features/account/requireSession";

/**
 * Ödeme adımlarının kapısı (add-cart-and-checkout D10): oturumsuz istek girişe
 * ve sonra ödeme sayfasına döner.
 */
export default async function CheckoutLayout({ children }: { readonly children: React.ReactNode }) {
  await requireSignedIn("/checkout");
  return children;
}
