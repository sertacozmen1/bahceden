"use client";

import { useRouter } from "next/navigation";
import { useTransition } from "react";
import { Languages } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { buttonVariants } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { LOCALES, parseLocale, saveLocale } from "@/i18n/locale";

/**
 * Dil menüsü: başlıkta tek ikon, altında Türkçe / English (ADR-030).
 *
 * Seçenekler her dilde kendi adıyla yazılır: dili anlamayan kullanıcı da kendi
 * dilini bulur. Seçim çereze yazılır ve sunucu bileşenleri yenilenir: metinler,
 * `<html lang>` ve `api`'nin mesajları aynı istekte yeni dilde gelir; adres
 * değişmez.
 */
export function LocaleMenu() {
  const t = useTranslations("locale");
  const locale = useLocale();
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  function choose(value: unknown) {
    const next = parseLocale(typeof value === "string" ? value : undefined);
    if (next === undefined || next === locale) {
      return;
    }
    saveLocale(next);
    startTransition(() => router.refresh());
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className={buttonVariants({ variant: "ghost", size: "icon" })}
        aria-label={t("trigger", { current: t(`names.${locale}`) })}
        aria-busy={pending}
      >
        <Languages aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        <DropdownMenuGroup>
          <DropdownMenuLabel>{t("label")}</DropdownMenuLabel>
          <DropdownMenuRadioGroup value={locale} onValueChange={choose}>
            {LOCALES.map((option) => (
              <DropdownMenuRadioItem key={option} value={option} lang={option}>
                {t(`names.${option}`)}
              </DropdownMenuRadioItem>
            ))}
          </DropdownMenuRadioGroup>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
