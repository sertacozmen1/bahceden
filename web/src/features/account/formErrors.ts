import { useTranslations } from "next-intl";
import { ApiRequestError } from "@/lib/apiError";

/**
 * Sunucu hatasını forma eşler (AGENTS.md §12.3): `fieldErrors` ilgili alanın
 * altına, geri kalan her şey formun üstüne. Mesajlar sunucudan geldiği gibi
 * gösterilir; otorite `api`'dir, form yalnızca erken geri bildirim verir.
 * Sunucu mesajları zaten sayfanın dilindedir (ADR-030).
 */
export type FormErrors = {
  readonly general?: string;
  readonly fields: Readonly<Record<string, string>>;
};

export const noErrors: FormErrors = { fields: {} };

/**
 * Sunucudan gelmeyen iki metin: sunucuya ulaşılamadığında ve yalnızca alan
 * hataları döndüğünde gösterilen genel cümle. Çağıran bileşen bunları kendi
 * çevirisinden verir; bu modül dil bilmez.
 */
export type FormErrorTexts = {
  readonly unavailable: string;
  readonly formHasErrors: string;
};

/** Formların ortak hata metinleri, seçili dilde. Üç form aynı iki cümleyi kullanır (K9). */
export function useFormErrorTexts(): FormErrorTexts {
  const t = useTranslations("account.errors");
  return { unavailable: t("unavailable"), formHasErrors: t("formHasErrors") };
}

export function toFormErrors(error: unknown, texts: FormErrorTexts): FormErrors {
  if (!(error instanceof ApiRequestError)) {
    return { general: texts.unavailable, fields: {} };
  }
  const fields: Record<string, string> = {};
  for (const fieldError of error.fieldErrors) {
    // Aynı alanın birden fazla hatası varsa ilki gösterilir: kullanıcı tek tek düzeltir.
    fields[fieldError.field] ??= fieldError.message;
  }
  return {
    general: error.fieldErrors.length > 0 ? texts.formHasErrors : error.message,
    fields,
  };
}
