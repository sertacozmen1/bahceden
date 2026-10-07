"use client";

import { useState } from "react";
import { Monitor, Moon, Sun, type LucideIcon } from "lucide-react";
import { useTranslations } from "next-intl";
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
import { applyTheme, parseTheme, THEMES, type Theme } from "@/lib/theme";

/**
 * Tema menüsü: başlıkta tek ikon, altında Açık / Koyu / Sistem (ADR-028).
 *
 * Tetikleyici seçili temanın ikonunu gösterir; erişilebilir adı seçimi de
 * söyler ("Tema: Koyu"), çünkü ikon tek başına anlam taşımaz. Seçim anında
 * `<html data-theme>`'yı değiştirir ve çereze yazar; sunucuya gidiş yok.
 */
const icons: Record<Theme, LucideIcon> = { light: Sun, dark: Moon, system: Monitor };

export function ThemeMenu({ initialTheme }: { readonly initialTheme: Theme }) {
  const t = useTranslations("theme");
  const [theme, setTheme] = useState<Theme>(initialTheme);
  const CurrentIcon = icons[theme];

  function choose(value: unknown) {
    // Menü değeri `unknown` döner; tanınmayan değer varsayılana düşer.
    const next = parseTheme(typeof value === "string" ? value : undefined);
    setTheme(next);
    applyTheme(next);
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className={buttonVariants({ variant: "ghost", size: "icon" })}
        aria-label={t("trigger", { current: t(theme) })}
      >
        <CurrentIcon aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        <DropdownMenuGroup>
          <DropdownMenuLabel>{t("label")}</DropdownMenuLabel>
          <DropdownMenuRadioGroup value={theme} onValueChange={choose}>
            {THEMES.map((option) => {
              const Icon = icons[option];
              return (
                <DropdownMenuRadioItem key={option} value={option}>
                  <Icon aria-hidden />
                  {t(option)}
                </DropdownMenuRadioItem>
              );
            })}
          </DropdownMenuRadioGroup>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
