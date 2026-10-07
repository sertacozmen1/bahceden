import type { LucideIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

/**
 * Tam sayfa durum kutusu: hata ve bulunamadı (AGENTS.md §12.5).
 *
 * Ne oldu (başlık + tek cümle) ve ne yapılabilir (tek eylem). İkon süstür,
 * anlamı metin taşır. Vitrin ve ileride satıcı paneli aynı biçimi kullanır;
 * bu yüzden `components/` altında, `ui/` değil (proje bileşeni).
 */
type StatusPanelProps = {
  readonly icon: LucideIcon;
  readonly title: string;
  readonly body: string;
  readonly role?: "alert";
  readonly children: React.ReactNode;
};

export function StatusPanel({ icon: Icon, title, body, role, children }: StatusPanelProps) {
  return (
    <main className="mx-auto w-full max-w-md px-4 pt-12 pb-12 lg:pt-20">
      <Card role={role}>
        <CardContent className="flex flex-col items-start gap-5">
          <span
            className={cn(
              "grid size-11 place-items-center rounded-full",
              role === "alert" ? "bg-destructive/10 text-destructive" : "bg-muted text-muted-foreground",
            )}
          >
            <Icon aria-hidden className="size-5.5" />
          </span>
          <div className="flex flex-col gap-2">
            <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
            <p className="text-pretty text-muted-foreground">{body}</p>
          </div>
          {children}
        </CardContent>
      </Card>
    </main>
  );
}
