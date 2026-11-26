import { compareDecimals, sumDecimals } from './decimal';

describe('sumDecimals', () => {
  it('adds exactly where floats drift', () => {
    // 0.1 + 0.2 is 0.30000000000000004 in floating point.
    expect(sumDecimals(['0.1', '0.2'])).toBe('0.3000');
    expect(sumDecimals(['162430.40', '21820.00'])).toBe('184250.4000');
  });

  it('keeps large balances exact', () => {
    expect(sumDecimals(['999999999999999.9999', '0.0001'])).toBe('1000000000000000.0000');
  });

  it('handles negatives, no values, and up to 4 decimals', () => {
    expect(sumDecimals(['-10.5', '2.25'])).toBe('-8.2500');
    expect(sumDecimals([])).toBe('0.0000');
    expect(sumDecimals(['1.2345'])).toBe('1.2345');
  });

  it('refuses anything that is not a decimal, rather than total it wrongly', () => {
    expect(() => sumDecimals(['1e3'])).toThrow();
    expect(() => sumDecimals(['1.23456'])).toThrow();
  });

  it('compares exactly, whatever the number of decimals', () => {
    expect(compareDecimals('100', '100.0000')).toBe(0);
    expect(compareDecimals('99.99', '100')).toBe(-1);
    expect(compareDecimals('1000000.01', '1000000')).toBe(1);
  });
});
