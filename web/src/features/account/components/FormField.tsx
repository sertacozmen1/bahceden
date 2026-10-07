import styles from "./AccountForm.module.css";

/**
 * Etiketli form alanı (AGENTS.md §12.4: her input etiketli). Hata metni alanla
 * `aria-describedby` üzerinden ilişkilendirilir; renk tek başına anlam taşımaz,
 * hata her zaman metinle söylenir.
 */
type FormFieldProps = {
  readonly id: string;
  readonly name: string;
  readonly label: string;
  readonly type: "email" | "password" | "text";
  readonly autoComplete: string;
  readonly hint?: string;
  readonly error?: string;
  readonly inputMode?: "numeric";
  readonly maxLength?: number;
};

export function FormField({ id, name, label, type, autoComplete, hint, error, inputMode, maxLength }: FormFieldProps) {
  const hintId = hint === undefined ? undefined : `${id}-ipucu`;
  const errorId = error === undefined ? undefined : `${id}-hata`;
  const describedBy = [hintId, errorId].filter((value) => value !== undefined).join(" ") || undefined;

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>
      <input
        className={styles.input}
        id={id}
        name={name}
        type={type}
        autoComplete={autoComplete}
        inputMode={inputMode}
        maxLength={maxLength}
        required
        aria-invalid={error !== undefined}
        aria-describedby={describedBy}
      />
      {hint !== undefined && (
        <p className={styles.hint} id={hintId}>
          {hint}
        </p>
      )}
      {error !== undefined && (
        <p className={styles.fieldError} id={errorId}>
          {error}
        </p>
      )}
    </div>
  );
}
