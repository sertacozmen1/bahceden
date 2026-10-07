import { CircleAlert, CircleCheck, Mail } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";

/**
 * Hesap formlarının tek bildirim biçimi: hata, başarı ve bilgi.
 *
 * Hata `role="alert"` ile sözü keser; başarı ve bilgi `role="status"` ile
 * sırası gelince okunur. İkon süstür (`aria-hidden`); anlamı metin taşır.
 * Formlar bu bileşeni kullanır, `Alert`'i doğrudan kurmaz (K9).
 */
type FormMessageProps = {
  readonly tone: "error" | "success" | "info";
  readonly children: React.ReactNode;
};

export function FormMessage({ tone, children }: FormMessageProps) {
  if (tone === "error") {
    return (
      <Alert variant="destructive" role="alert">
        <CircleAlert aria-hidden />
        <AlertDescription>{children}</AlertDescription>
      </Alert>
    );
  }
  const Icon = tone === "success" ? CircleCheck : Mail;
  return (
    <Alert variant={tone === "success" ? "success" : "default"} role="status">
      <Icon aria-hidden />
      <AlertDescription>{children}</AlertDescription>
    </Alert>
  );
}
