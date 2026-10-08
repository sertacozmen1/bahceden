import { requireSignedIn } from "@/features/account/requireSession";

/**
 * Sepet kapısı (add-cart-and-checkout D4): sepet hesaba bağlıdır, oturumsuz
 * istek girişe ve sonra sepete döner.
 */
export default async function CartLayout({ children }: { readonly children: React.ReactNode }) {
  await requireSignedIn("/cart");
  return children;
}
