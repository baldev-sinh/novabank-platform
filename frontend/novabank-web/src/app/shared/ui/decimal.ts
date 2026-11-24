/** The ledger's scale: NUMERIC(19,4) (Decision L). */
const SCALE = 4;
const FACTOR = 10n ** BigInt(SCALE);
const DECIMAL = /^(-)?(\d+)(?:\.(\d{1,4}))?$/;

/**
 * Adds API amounts exactly (as scaled BigInts, never binary floats), e.g. a total across accounts.
 * Returns a decimal string with 4 places, ready for the money pipe. Throws on a malformed amount,
 * because a total that silently skipped one would be wrong.
 */
export function sumDecimals(values: readonly string[]): string {
  let total = 0n;
  for (const value of values) {
    total += toScaled(value);
  }
  return fromScaled(total);
}

function toScaled(value: string): bigint {
  const match = DECIMAL.exec(value);
  if (!match) {
    throw new Error(`Not a decimal amount: ${value}`);
  }
  const [, minus, whole, fraction = ''] = match;
  const scaled = BigInt(whole) * FACTOR + BigInt(fraction.padEnd(SCALE, '0'));
  return minus ? -scaled : scaled;
}

function fromScaled(scaled: bigint): string {
  const negative = scaled < 0n;
  const magnitude = negative ? -scaled : scaled;
  const whole = magnitude / FACTOR;
  const fraction = (magnitude % FACTOR).toString().padStart(SCALE, '0');
  return `${negative ? '-' : ''}${whole}.${fraction}`;
}
