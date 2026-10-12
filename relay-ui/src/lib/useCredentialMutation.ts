import { useRef } from 'react';
import { useMutation } from '@tanstack/react-query';

/**
 * A mutation for calls that carry credentials (admin token, a candidate API key) or return them
 * (a new API key). TanStack keeps a mutation's `variables` and `data` in its MutationCache; here
 * the arguments travel through a ref that is cleared as the call starts, the result is handed to
 * `onSuccess` and dropped, and the cached mutation holds neither.
 */
export function useCredentialMutation<A, R>(
  run: (args: A) => Promise<R>,
  handlers: {
    onSuccess: (result: R, args: A) => void;
    onError: (e: unknown) => void;
    onSettled?: () => void;
  },
) {
  const pending = useRef<A | null>(null);
  const done = useRef<{ result: R; args: A } | null>(null);
  const m = useMutation({
    gcTime: 0,
    mutationFn: async (): Promise<null> => {
      const args = pending.current;
      pending.current = null;
      if (args === null) throw new Error('mutation started without arguments');
      done.current = { result: await run(args), args };
      return null;
    },
    onSuccess: () => {
      const d = done.current;
      done.current = null;
      if (d) handlers.onSuccess(d.result, d.args);
    },
    onError: (e) => handlers.onError(e),
    onSettled: () => handlers.onSettled?.(),
  });
  return {
    mutate(args: A) {
      pending.current = args;
      m.mutate();
    },
    isPending: m.isPending,
  };
}
