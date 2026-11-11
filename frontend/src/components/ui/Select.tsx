import { forwardRef, useId, type SelectHTMLAttributes } from 'react';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  options: SelectOption[];
  placeholder?: string;
  hint?: string;
  error?: string;
  containerClassName?: string;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  function Select(
    {
      label,
      options,
      placeholder,
      hint,
      error,
      required,
      id,
      className = '',
      containerClassName = '',
      'aria-describedby': ariaDescribedBy,
      ...rest
    },
    ref,
  ) {
    const generatedId = useId();
    const selectId = id ?? generatedId;
    const hintId = hint ? `${selectId}-hint` : undefined;
    const errorId = error ? `${selectId}-error` : undefined;
    const describedBy =
      [ariaDescribedBy, hintId, errorId].filter(Boolean).join(' ') || undefined;

    return (
      <div className={`ui-field ${containerClassName}`.trim()}>
        <label className="ui-field__label" htmlFor={selectId}>
          {label}
          {required ? (
            <span className="ui-field__required" aria-hidden="true">
              *
            </span>
          ) : null}
        </label>
        <select
          ref={ref}
          id={selectId}
          required={required}
          aria-required={required || undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`ui-select ${error ? 'ui-select--error' : ''} ${className}`.trim()}
          {...rest}
        >
          {placeholder ? (
            <option value="" disabled>
              {placeholder}
            </option>
          ) : null}
          {options.map((option) => (
            <option
              key={option.value}
              value={option.value}
              disabled={option.disabled}
            >
              {option.label}
            </option>
          ))}
        </select>
        {hint ? (
          <span id={hintId} className="ui-field__hint">
            {hint}
          </span>
        ) : null}
        {error ? (
          <span id={errorId} className="ui-field__error" role="alert">
            {error}
          </span>
        ) : null}
      </div>
    );
  },
);

