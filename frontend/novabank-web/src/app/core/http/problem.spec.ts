import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { fieldErrors, problemOf, ProblemType } from './problem';

describe('problemOf', () => {
  it('reads an RFC 9457 problem with the trace id and field errors', () => {
    const problem = problemOf(
      new HttpErrorResponse({
        status: 400,
        error: {
          type: 'about:blank',
          title: 'Bad Request',
          status: 400,
          traceId: 'abc123',
          errors: [
            { field: 'amount', message: 'amount must be a positive decimal' },
            { field: 'amount', message: 'second message' },
            { nonsense: true },
          ],
        },
      }),
    );

    expect(problem.status).toBe(400);
    expect(problem.traceId).toBe('abc123');
    expect(problem.errors).toHaveLength(2);
    expect(fieldErrors(problem)).toEqual({ amount: 'amount must be a positive decimal' });
  });

  it('keeps the problem type the page branches on', () => {
    const problem = problemOf(
      new HttpErrorResponse({
        status: 403,
        error: { type: ProblemType.EMAIL_NOT_VERIFIED, title: 'Email not verified', status: 403 },
      }),
    );
    expect(problem.type).toBe(ProblemType.EMAIL_NOT_VERIFIED);
  });

  it('reads Retry-After in seconds', () => {
    const problem = problemOf(
      new HttpErrorResponse({
        status: 429,
        headers: new HttpHeaders({ 'Retry-After': '120' }),
        error: { type: ProblemType.RATE_LIMITED, title: 'Too Many Requests', status: 429 },
      }),
    );
    expect(problem.retryAfterSeconds).toBe(120);
  });

  it('tells a network failure from a server answer', () => {
    expect(problemOf(new HttpErrorResponse({ status: 0 })).type).toBe(ProblemType.NETWORK);
  });

  it('copes with a body that is not a problem, and with non-HTTP errors', () => {
    const html = problemOf(
      new HttpErrorResponse({ status: 502, statusText: 'Bad Gateway', error: '<html>' }),
    );
    expect(html).toEqual({ type: ProblemType.UNKNOWN, title: 'Bad Gateway', status: 502 });
    expect(problemOf(new Error('boom')).type).toBe(ProblemType.UNKNOWN);
  });
});
