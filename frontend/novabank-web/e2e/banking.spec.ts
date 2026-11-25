import { BrowserContext, expect, Page, test } from '@playwright/test';
import {
  approveKyc,
  deposit,
  expectAccessible,
  PASSWORD,
  staffToken,
  userIdOf,
  verificationLink,
  watchForProblems,
} from './support';

/**
 * One new customer's way through the bank, in a real browser against the real stack: register,
 * verify, onboard, get approved, open accounts, receive money, send money. Every page on the way
 * is checked with axe, and the strict CSP must not block anything.
 */
test.describe.serial('a new customer', () => {
  const run = Date.now();
  const email = `web-e2e-${run}@example.com`;
  let context: BrowserContext;
  let page: Page;
  let problems: string[];
  let staff: string;

  test.beforeAll(async ({ browser }) => {
    staff = await staffToken(`web-e2e-staff-${run}@example.com`);
    // An explicit context: axe can only analyse pages that belong to one.
    context = await browser.newContext();
    page = await context.newPage();
    problems = watchForProblems(page);
  });

  test.afterAll(async () => {
    await context.close();
  });

  test.afterEach(() => {
    expect(problems).toEqual([]);
  });

  test('registers and verifies their email address', async () => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Sign in to online banking' })).toBeVisible();
    await expectAccessible(page);

    await page.getByRole('link', { name: 'Register for online banking' }).click();
    // Wait for the (lazy-loaded) page: the sign-in page has an "Email address" field too, and
    // typing before the switch fills the wrong one.
    await expect(page.getByRole('heading', { name: 'Register for online banking' })).toBeVisible();
    await page.getByLabel('Email address').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
    await expectAccessible(page);
    await page.getByRole('button', { name: 'Create my sign-in' }).click();
    await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible();
    await expectAccessible(page);

    await page.goto(await verificationLink(email));
    await expect(page.getByText('Your email address is verified.')).toBeVisible();
    // The token is gone from the address bar.
    expect(page.url()).not.toContain('token=');
  });

  test('signs in and sends their details for the identity check', async () => {
    await page.getByLabel('Email address').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('heading', { name: 'Tell us about yourself' })).toBeVisible();
    await expectAccessible(page);

    await page.getByLabel('First name').fill('Priya');
    await page.getByLabel('Last name').fill('Sharma');
    await page.getByLabel('Day').fill('15');
    await page.getByLabel('Month').fill('1');
    await page.getByLabel('Year').fill('1990');
    await page.getByLabel('Mobile number').fill('+91 98765 43210');
    await page.getByRole('button', { name: 'Send for identity check' }).click();
    await expect(page.getByRole('heading', { name: "We're checking your identity" })).toBeVisible();
    await expectAccessible(page);
  });

  test('once approved, opens a current and a savings account', async () => {
    await approveKyc(staff, await userIdOf(email));
    await page.getByRole('button', { name: 'Check again' }).click();
    await expect(page.getByText("You don't have an account yet")).toBeVisible();
    await expectAccessible(page);

    // The account service hears about the approval through Kafka: a 403 is explained and retried.
    await expect(async () => {
      await page.goto('/accounts/open');
      await page.getByRole('button', { name: 'Open account' }).click();
      await expect(page.getByRole('heading', { name: 'Current account' })).toBeVisible({
        timeout: 5000,
      });
    }).toPass({ timeout: 60_000 });

    await page.goto('/accounts/open');
    await expectAccessible(page);
    await page.getByText('Savings account').first().click();
    await page.getByRole('button', { name: 'Open account' }).click();
    await expect(page.getByRole('heading', { name: 'Savings account' })).toBeVisible();

    await page.goto('/');
    // Both turn Active when the ledger confirms them, without a reload.
    await expect(page.locator('nb-badge', { hasText: 'Active' })).toHaveCount(2, {
      timeout: 60_000,
    });
    await expectAccessible(page);
  });

  test('receives a deposit and sends a transfer', async () => {
    const current = page.getByRole('link', { name: /Current account/ });
    const currentId = (await current.getAttribute('href'))!.split('/').pop()!;
    await deposit(staff, currentId, '25000.00');
    await page.reload();
    await expect(page.getByText('₹25,000.00').first()).toBeVisible();

    const savingsCard = page.getByRole('link', { name: /Savings account/ });
    const savingsLast4 = (/ending (\d{4})/.exec((await savingsCard.textContent()) ?? '') ?? [])[1];
    await savingsCard.click();
    const savingsNumber = (await page.locator('.summary__number strong').textContent())!.replace(
      /\s/g,
      '',
    );
    expect(savingsNumber.endsWith(savingsLast4!)).toBe(true);

    await page.getByRole('link', { name: 'Transfer', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Transfer money' })).toBeVisible();
    await page.getByLabel('To account number').fill(savingsNumber);
    await page.getByLabel('Amount').fill('7500.25');
    await page.getByLabel('Reference').fill('Rent share');
    await expectAccessible(page);
    await page.getByRole('button', { name: 'Continue' }).click();
    await expect(page.getByRole('heading', { name: 'Check the details' })).toBeVisible();
    await expectAccessible(page);
    await page.getByRole('button', { name: /Confirm and send/ }).click();
    await expect(page.getByRole('heading', { name: 'Transfer sent' })).toBeVisible({
      timeout: 40_000,
    });
    await expectAccessible(page);

    await page.goto(`/accounts/${currentId}`);
    await expect(page.getByRole('cell', { name: /Rent share/ })).toBeVisible();
    await expect(page.getByText('₹17,499.75').first()).toBeVisible();
    await expectAccessible(page);
  });
});
