import { ApiError, ContractError, NetworkError } from './http';
import type { FieldError } from './schemas';

export type ErrorDescription = {
  title: string;
  detail: string;
  /** Backend field errors, for mapping onto form inputs. */
  fieldErrors: FieldError[];
};

const SSRF_HINT =
  'Relay refuses loopback, private and link-local addresses (SSRF protection). Use a public URL, or for a local demo start the backend with RELAY_SSRF_ALLOW_PRIVATE_NETWORKS=true.';

/** Friendlier wording for specific backend field messages. The backend text is kept as-is otherwise. */
export function explainFieldError(fe: FieldError): string {
  if (fe.field === 'url' && /private|loopback/i.test(fe.message))
    return `${fe.message}. ${SSRF_HINT}`;
  if (fe.field === 'eventTypeFilter') {
    return `${fe.message}: use lowercase letters, digits, ".", "_", "-" and "*" (for example "*", "order.*" or "order.paid").`;
  }
  return fe.message;
}

function describeApiError(e: ApiError): ErrorDescription {
  const fieldErrors = e.fieldErrors;
  const base = { fieldErrors };
  if (e.body === null) {
    // Not a Relay ApiError body: usually the dev proxy or nginx answering because relay-server is down.
    return {
      ...base,
      title: `HTTP ${e.status}`,
      detail:
        e.status >= 500
          ? 'No usable answer from Relay. Is relay-server running and reachable from the proxy?'
          : 'The server answered with an unexpected error.',
    };
  }
  switch (e.code) {
    case 'VALIDATION_ERROR':
      return {
        ...base,
        title: 'Please fix the highlighted fields',
        detail:
          fieldErrors.length > 0
            ? fieldErrors.map((f) => `${f.field}: ${explainFieldError(f)}`).join(' ')
            : e.message,
      };
    case 'MALFORMED_REQUEST':
      return {
        ...base,
        title: 'Request not understood',
        detail: 'Relay could not read the request body.',
      };
    case 'UNAUTHORIZED':
      return { ...base, title: 'Not authorized', detail: e.message };
    case 'NOT_FOUND':
      return {
        ...base,
        title: 'Not found',
        detail: `${e.message}. It may have been deleted, or it belongs to another app.`,
      };
    case 'CONFLICT':
      return { ...base, title: 'Conflict', detail: e.message };
    case 'PAYLOAD_TOO_LARGE':
      return {
        ...base,
        title: 'Payload too large',
        detail: `${e.message}. Send less data, or a reference to it.`,
      };
    case 'INTERNAL_ERROR':
      return {
        ...base,
        title: 'Relay had an internal error',
        detail: 'Try again. If it keeps happening, check the relay-server logs.',
      };
    default:
      return { ...base, title: `Request failed (${e.status})`, detail: e.message };
  }
}

/** Turns any thrown value into readable text. Never returns raw JSON or stack traces. */
export function describeError(e: unknown): ErrorDescription {
  if (e instanceof ApiError) return describeApiError(e);
  if (e instanceof NetworkError) {
    return {
      title: 'Cannot reach Relay',
      detail:
        e.baseUrl === ''
          ? 'The request did not get an answer. Is relay-server running (the dev proxy forwards /api to it)?'
          : `The request to ${e.baseUrl} did not get an answer. Check the URL, that the backend is running, and CORS.`,
      fieldErrors: [],
    };
  }
  if (e instanceof ContractError) {
    return {
      title: 'Unexpected response from Relay',
      detail: `The response from ${e.path} did not match what this console expects (${e.issues.slice(0, 2).join('; ')}). The UI and backend versions may not match.`,
      fieldErrors: [],
    };
  }
  return {
    title: 'Something went wrong',
    detail: 'An unexpected error occurred.',
    fieldErrors: [],
  };
}

export function isApiError(e: unknown, status?: number): e is ApiError {
  return e instanceof ApiError && (status === undefined || e.status === status);
}
