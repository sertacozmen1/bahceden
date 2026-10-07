import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

/**
 * Hesap ekranlarının ortak iskeleti: dar tek sütun, başlık, kısa giriş
 * cümlesi, kart içinde form ve isteğe bağlı alt not (Miller: form küçük
 * gruplarda, tek odak).
 *
 * Sayfalar aynı ölçüyü burada paylaşır; iskelet ve hata durumları da bunu
 * kullanır ki içerik geldiğinde düzen sıçramasın.
 */
type AccountScreenProps = {
  readonly title?: React.ReactNode;
  readonly intro?: React.ReactNode;
  readonly aside?: React.ReactNode;
  readonly children: React.ReactNode;
  readonly panelRole?: "alert" | "status";
  readonly className?: string;
} & Pick<React.ComponentProps<"main">, "aria-busy" | "aria-label">;

export function AccountScreen({ title, intro, aside, children, panelRole, className, ...mainProps }: AccountScreenProps) {
  return (
    <main className={cn("mx-auto w-full max-w-md px-4 pt-10 pb-12 lg:pt-16", className)} {...mainProps}>
      {title !== undefined && <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{title}</h1>}
      {intro !== undefined && <p className="mt-2 text-pretty text-muted-foreground">{intro}</p>}
      <Card className={title === undefined && intro === undefined ? undefined : "mt-6"} role={panelRole}>
        <CardContent className="flex flex-col gap-5">{children}</CardContent>
      </Card>
      {aside !== undefined && <p className="mt-5 text-center text-sm text-muted-foreground">{aside}</p>}
    </main>
  );
}
