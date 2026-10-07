import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Sınıf adlarını birleştirir; çakışan Tailwind sınıflarında sonuncusu kazanır
 * (shadcn/ui sözleşmesi, ADR-027). Bileşenler koşullu sınıfı yalnızca bununla
 * kurar; ikinci bir birleştirici yazılmaz (K9).
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
