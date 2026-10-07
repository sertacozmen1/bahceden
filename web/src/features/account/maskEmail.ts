/**
 * Ekranda e-posta maskeleme: `alici@ornek.test` → `a***@ornek.test`.
 *
 * Üst başlık her sayfada görünür ve ekran paylaşımında veya omuz üstünden
 * okunabilir; tam adresi göstermek gereksiz kişisel veri ifşası olurdu (K10
 * maskeleme biçimiyle aynı). Tam adres yalnızca "Hesabım" sayfasındadır.
 */
export function maskEmail(email: string): string {
  const at = email.indexOf("@");
  if (at <= 0) {
    return "***";
  }
  return `${email.charAt(0)}***${email.slice(at)}`;
}
