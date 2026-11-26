import { HttpErrorResponse } from '@angular/common/http';

/** One invalid field, as every NovaBank service reports it (Decision D). */
export interface FieldError {
  field: string;
  message: string;
}

/**
 * An RFC 9457 problem as the services send it, plus the two cases a browser adds: no answer at all
 * (`status` 0: offline, blocked, or the service is down) and an answer that isn't a problem.
 */
export interface Problem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  /** Quote it to support: it finds the request's logs and trace (Decision R). */
  traceId?: string;
  errors?: FieldError[];
  /** Seconds, from a 429's or 503's Retry-After header. */
  retryAfterSeconds?: number;
}

export const ProblemType = {
  EMAIL_NOT_VERIFIED: 'urn:novabank:problem:email-not-verified',
  RATE_LIMITED: 'urn:novabank:problem:rate-limited',
  NETWORK: 'about:network-error',
  UNKNOWN: 'about:blank',
} as const;

export function problemOf(error: unknown): Problem {
  if (!(error instanceof HttpErrorResponse)) {
    return { type: ProblemType.UNKNOWN, title: 'Something went wrong', status: 0 };
  }
  if (error.status === 0) {
    return { type: ProblemType.NETWORK, title: 'No connection', status: 0 };
  }
  const body: unknown = error.error;
  const problem: Problem = isProblemBody(body)
    ? {
        type: body.type ?? ProblemType.UNKNOWN,
        title: body.title ?? error.statusText,
        status: body.status ?? error.status,
        detail: body.detail,
        traceId: body.traceId,
        errors: Array.isArray(body.errors) ? body.errors.filter(isFieldError) : undefined,
      }
    : { type: ProblemType.UNKNOWN, title: error.statusText, status: error.status };
  const retryAfter = Number(error.headers?.get('Retry-After'));
  if (Number.isFinite(retryAfter) && retryAfter > 0) {
    problem.retryAfterSeconds = retryAfter;
  }
  return problem;
}

/** The first message per field, keyed by field name, for showing next to each control. */
export function fieldErrors(problem: Problem): Record<string, string> {
  const byField: Record<string, string> = {};
  for (const error of problem.errors ?? []) {
    byField[error.field] ??= error.message;
  }
  return byField;
}

interface ProblemBody {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  traceId?: string;
  errors?: unknown[];
}

function isProblemBody(body: unknown): body is ProblemBody {
  return typeof body === 'object' && body !== null && ('title' in body || 'type' in body);
}

function isFieldError(value: unknown): value is FieldError {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as FieldError).field === 'string' &&
    typeof (value as FieldError).message === 'string'
  );
}
