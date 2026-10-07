import { ApiRequestError } from "@/lib/apiError";
import { texts } from "@/lib/texts";

/**
 * Sunucu hatasını forma eşler (AGENTS.md §12.3): `fieldErrors` ilgili alanın
 * altına, geri kalan her şey formun üstüne. Mesajlar sunucudan geldiği gibi
 * gösterilir; otorite `api`'dir, form yalnızca erken geri bildirim verir.
 */
export type FormErrors = {
  readonly general?: string;
  readonly fields: Readonly<Record<string, string>>;
};

export const noErrors: FormErrors = { fields: {} };

export function toFormErrors(error: unknown): FormErrors {
  if (!(error instanceof ApiRequestError)) {
    return { general: texts.account.errors.unavailable, fields: {} };
  }
  const fields: Record<string, string> = {};
  for (const fieldError of error.fieldErrors) {
    // Aynı alanın birden fazla hatası varsa ilki gösterilir: kullanıcı tek tek düzeltir.
    fields[fieldError.field] ??= fieldError.message;
  }
  return {
    general: error.fieldErrors.length > 0 ? texts.account.errors.formHasErrors : error.message,
    fields,
  };
}
