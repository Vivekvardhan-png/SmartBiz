import {
  forwardRef,
  useId,
  type InputHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';

interface SharedFieldProps {
  label: string;
  hint?: string;
  error?: string;
  required?: boolean;
  containerClassName?: string;
}

export interface FormFieldProps
  extends SharedFieldProps,
    InputHTMLAttributes<HTMLInputElement> {}

export const FormField = forwardRef<HTMLInputElement, FormFieldProps>(
  function FormField(
    {
      label,
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
    const inputId = id ?? generatedId;
    const hintId = hint ? `${inputId}-hint` : undefined;
    const errorId = error ? `${inputId}-error` : undefined;
    const describedBy =
      [ariaDescribedBy, hintId, errorId].filter(Boolean).join(' ') || undefined;

    return (
      <div className={`ui-field ${containerClassName}`.trim()}>
        <label className="ui-field__label" htmlFor={inputId}>
          {label}
          {required ? (
            <span className="ui-field__required" aria-hidden="true">
              *
            </span>
          ) : null}
        </label>
        <input
          ref={ref}
          id={inputId}
          required={required}
          aria-required={required || undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`ui-input ${error ? 'ui-input--error' : ''} ${className}`.trim()}
          {...rest}
        />
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

export interface TextareaFieldProps
  extends SharedFieldProps,
    TextareaHTMLAttributes<HTMLTextAreaElement> {}

export const TextareaField = forwardRef<HTMLTextAreaElement, TextareaFieldProps>(
  function TextareaField(
    {
      label,
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
    const textareaId = id ?? generatedId;
    const hintId = hint ? `${textareaId}-hint` : undefined;
    const errorId = error ? `${textareaId}-error` : undefined;
    const describedBy =
      [ariaDescribedBy, hintId, errorId].filter(Boolean).join(' ') || undefined;

    return (
      <div className={`ui-field ${containerClassName}`.trim()}>
        <label className="ui-field__label" htmlFor={textareaId}>
          {label}
          {required ? (
            <span className="ui-field__required" aria-hidden="true">
              *
            </span>
          ) : null}
        </label>
        <textarea
          ref={ref}
          id={textareaId}
          required={required}
          aria-required={required || undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`ui-textarea ${error ? 'ui-textarea--error' : ''} ${className}`.trim()}
          {...rest}
        />
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

