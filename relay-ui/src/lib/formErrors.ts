import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { describeError, explainFieldError, sentence } from '@/api/errors';

/**
 * Puts backend field errors on the matching inputs and anything else on the form root.
 * Returns true if at least one error was placed on a known field.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: ReadonlyArray<Path<T>>,
): boolean {
  const d = describeError(error);
  let placed = false;
  const unplaced: string[] = [];
  for (const fe of d.fieldErrors) {
    const field = knownFields.find((k) => k === fe.field);
    if (field) {
      setError(field, { type: 'server', message: explainFieldError(fe) });
      placed = true;
    } else {
      unplaced.push(`${fe.field}: ${explainFieldError(fe)}`);
    }
  }
  if (!placed || unplaced.length > 0) {
    setError('root.server' as Path<T>, {
      type: 'server',
      message:
        unplaced.length > 0 ? unplaced.join(' ') : `${sentence(d.title)} ${sentence(d.detail)}`,
    });
  }
  return placed;
}
