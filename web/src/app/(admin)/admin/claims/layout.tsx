import { requireRole } from "@/features/account/requireSession";

/** Admin talep ekranının kapısı (ADR-086): oturum yoksa giriş, rol yoksa "bulunamadı". */
export default async function AdminClaimsLayout({ children }: { readonly children: React.ReactNode }) {
  await requireRole("/admin/claims", "ROLE_ADMIN");
  return children;
}
