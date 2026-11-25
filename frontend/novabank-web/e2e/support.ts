import AxeBuilder from '@axe-core/playwright';
import { expect, Page } from '@playwright/test';

/**
 * The services the browser doesn't reach: staff actions (roles, KYC approval, deposits) are not on
 * the public /api/v1 routes of the web container, so the tests call those services directly, as
 * scripts/e2e/money-flow.sh does.
 */
export const AUTH = process.env['E2E_AUTH_URL'] ?? 'http://localhost:8081/api/v1';
export const CUSTOMER = process.env['E2E_CUSTOMER_URL'] ?? 'http://localhost:8082/api/v1';
export const PAYMENT = process.env['E2E_PAYMENT_URL'] ?? 'http://localhost:8085/api/v1';
export const MAILPIT = process.env['E2E_MAILPIT_URL'] ?? 'http://localhost:8025';
/** compose's (and money-flow.sh's) bootstrap administrator. */
export const ADMIN_EMAIL = process.env['E2E_ADMIN_EMAIL'] ?? 'admin@novabank.local';
export const PASSWORD = 'correct horse battery staple';

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

async function call(url: string, init: RequestInit = {}, token?: string): Promise<Response> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return fetch(url, { ...init, headers });
}

/** The verification link auth emailed to `email` (Mailpit catches every email). */
export async function verificationLink(email: string): Promise<string> {
  for (let attempt = 0; attempt < 30; attempt++) {
    const search = await (
      await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`)
    ).json();
    if (search.messages?.length) {
      const message = await (
        await fetch(`${MAILPIT}/api/v1/message/${search.messages[0].ID}`)
      ).json();
      const link = /https?:\/\/\S+verify-email#token=[A-Za-z0-9_-]+/.exec(message.Text)?.[0];
      if (link) return link;
    }
    await sleep(1000);
  }
  throw new Error(`No verification email for ${email}`);
}

async function login(email: string): Promise<string> {
  const response = await call(`${AUTH}/auth/login`, {
    method: 'POST',
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  if (!response.ok) throw new Error(`login ${email}: ${response.status}`);
  return (await response.json()).accessToken;
}

async function registerAndVerify(email: string): Promise<void> {
  const response = await call(`${AUTH}/users/register`, {
    method: 'POST',
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  if (response.status === 409) return; // already there (e.g. money-flow.sh made the admin)
  if (response.status !== 201) throw new Error(`register ${email}: ${response.status}`);
  const token = new URL(await verificationLink(email)).hash.split('token=')[1];
  const verified = await call(`${AUTH}/users/verify-email`, {
    method: 'POST',
    body: JSON.stringify({ token }),
  });
  if (!verified.ok) throw new Error(`verify ${email}: ${verified.status}`);
}

/** A member of staff with COMPLIANCE_OFFICER and OPERATIONS, granted by the bootstrap admin. */
export async function staffToken(email: string): Promise<string> {
  await registerAndVerify(email);
  await registerAndVerify(ADMIN_EMAIL);
  const admin = await login(ADMIN_EMAIL);
  const lookup = await call(
    `${AUTH}/admin/users/lookup`,
    { method: 'POST', body: JSON.stringify({ email }) },
    admin,
  );
  const { userId } = await lookup.json();
  for (const role of ['COMPLIANCE_OFFICER', 'OPERATIONS']) {
    const granted = await call(
      `${AUTH}/admin/users/${userId}/roles/${role}`,
      { method: 'PUT' },
      admin,
    );
    if (!granted.ok) throw new Error(`grant ${role}: ${granted.status}`);
  }
  return login(email);
}

export async function approveKyc(staff: string, userId: string): Promise<void> {
  const queue: { customerId: string; userId: string }[] = await (
    await call(`${CUSTOMER}/compliance/customers/pending-kyc`, {}, staff)
  ).json();
  const customer = queue.find((c) => c.userId === userId);
  if (!customer) throw new Error('Customer not in the KYC queue');
  const approved = await call(
    `${CUSTOMER}/compliance/customers/${customer.customerId}/kyc-approval`,
    { method: 'POST' },
    staff,
  );
  if (!approved.ok) throw new Error(`approve: ${approved.status}`);
}

export async function userIdOf(email: string): Promise<string> {
  const me = await call(`${AUTH}/auth/me`, {}, await login(email));
  return (await me.json()).userId;
}

/** Operations deposit `amount` into the account, and wait until it has completed. */
export async function deposit(staff: string, accountId: string, amount: string): Promise<void> {
  const started = await call(
    `${PAYMENT}/admin/deposits`,
    {
      method: 'POST',
      headers: { 'Idempotency-Key': `e2e-web-${crypto.randomUUID()}` },
      body: JSON.stringify({
        toAccountId: accountId,
        amount,
        currency: 'INR',
        reference: 'Opening deposit',
      }),
    },
    staff,
  );
  const { paymentId } = await started.json();
  for (let attempt = 0; attempt < 60; attempt++) {
    const payment = await (await call(`${PAYMENT}/admin/payments/${paymentId}`, {}, staff)).json();
    if (payment.status === 'COMPLETED') return;
    if (payment.status === 'FAILED') throw new Error(`deposit failed: ${payment.failureReason}`);
    await sleep(1000);
  }
  throw new Error('deposit did not complete');
}

/**
 * No WCAG 2.0/2.1 A or AA violations axe can find on the page as it is now
 * (https://playwright.dev/docs/accessibility-testing). Automated checks are a floor, not a
 * substitute for testing with assistive technology.
 */
export async function expectAccessible(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.help} (${v.nodes.length})`)).toEqual([]);
}

/** Collects what would show a broken page: uncaught errors and CSP violations. */
export function watchForProblems(page: Page): string[] {
  const problems: string[] = [];
  page.on('pageerror', (error) => problems.push(`page error: ${error.message}`));
  page.on('console', (message) => {
    if (message.type() === 'error' && /Content.Security.Policy/i.test(message.text())) {
      problems.push(`CSP: ${message.text()}`);
    }
  });
  return problems;
}
