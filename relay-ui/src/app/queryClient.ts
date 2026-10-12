import { QueryClient } from '@tanstack/react-query';
import { ApiError, ContractError } from '@/api/http';

/** 4xx and contract errors are never retried: retrying a rejected key or a bad request just repeats it. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ContractError) return false;
  if (error instanceof ApiError && error.status < 500) return false;
  return failureCount < 2;
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, staleTime: 5_000, refetchOnWindowFocus: true },
      mutations: { retry: false },
    },
  });
}
