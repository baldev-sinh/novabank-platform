import { Pipe, PipeTransform } from '@angular/core';

/** CREDIT = money in (shown with +), DEBIT = money out (shown with −), as on a statement. */
export type MoneyDirection = 'CREDIT' | 'DEBIT';

const DECIMAL = /^-?\d+(\.\d+)?$/;
const MINUS = '−'; // a real minus sign, not a hyphen
const formatters = new Map<string, Intl.NumberFormat>();

/**
 * Formats an API amount (a decimal string with a currency, Decision L) for display, in Indian
 * grouping: `'162430.4' | nbMoney: 'INR'` → `₹1,62,430.40`.
 *
 * The string goes to `Intl.NumberFormat` as it is: since ES2023 a numeric string is formatted as an
 * exact decimal, so an amount never passes through a binary float on its way to the screen.
 */
@Pipe({ name: 'nbMoney' })
export class MoneyPipe implements PipeTransform {
  transform(
    amount: string | null | undefined,
    currency: string | null | undefined,
    direction?: MoneyDirection | null,
  ): string {
    if (amount == null || currency == null || !DECIMAL.test(amount)) {
      return '';
    }
    const negative = amount.startsWith('-');
    const magnitude = (negative ? amount.slice(1) : amount) as `${number}`;
    const formatted = formatter(currency).format(magnitude);
    if (direction === 'CREDIT') {
      return '+' + formatted;
    }
    if (direction === 'DEBIT' || negative) {
      return MINUS + formatted;
    }
    return formatted;
  }
}

function formatter(currency: string): Intl.NumberFormat {
  let cached = formatters.get(currency);
  if (!cached) {
    cached = new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency,
      currencyDisplay: 'narrowSymbol',
      // The sign is added above (+ / −); fraction digits default to the currency's minor units,
      // which the API already validates amounts against (Decision L).
      signDisplay: 'never',
    });
    formatters.set(currency, cached);
  }
  return cached;
}
