import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

/**
 * Etiketli form alanı (AGENTS.md §12.4: her input etiketli). Hata metni alanla
 * `aria-describedby` üzerinden ilişkilendirilir; renk tek başına anlam taşımaz,
 * hata her zaman metinle söylenir. Etiket üstte, yardım ve hata altta.
 */
type FormFieldProps = {
  readonly id: string;
  readonly name: string;
  readonly label: string;
  readonly type: "email" | "password" | "text" | "tel";
  readonly autoComplete: string;
  readonly hint?: string;
  readonly error?: string;
  readonly inputMode?: "numeric" | "decimal";
  readonly maxLength?: number;
  /** Düzeltip yeniden gönderilen formda önceki değer. */
  readonly defaultValue?: string;
  readonly className?: string;
};

export function FormField({
  id,
  name,
  label,
  type,
  autoComplete,
  hint,
  error,
  inputMode,
  maxLength,
  defaultValue,
  className,
}: FormFieldProps) {
  const hintId = hint === undefined ? undefined : `${id}-ipucu`;
  const errorId = error === undefined ? undefined : `${id}-hata`;
  const describedBy = [hintId, errorId].filter((value) => value !== undefined).join(" ") || undefined;

  return (
    <Field data-invalid={error !== undefined}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Input
        className={className}
        id={id}
        name={name}
        type={type}
        autoComplete={autoComplete}
        inputMode={inputMode}
        maxLength={maxLength}
        defaultValue={defaultValue}
        required
        aria-invalid={error !== undefined}
        aria-describedby={describedBy}
      />
      {hint !== undefined && <FieldDescription id={hintId}>{hint}</FieldDescription>}
      {error !== undefined && <FieldError id={errorId}>{error}</FieldError>}
    </Field>
  );
}
