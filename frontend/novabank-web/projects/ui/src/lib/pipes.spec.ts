import { AccountNumberPipe } from './account-number.pipe';
import { MoneyPipe } from './money.pipe';

describe('MoneyPipe', () => {
  const money = new MoneyPipe();

  it('formats rupees in Indian grouping with two decimals', () => {
    expect(money.transform('162430.4', 'INR')).toBe('₹1,62,430.40');
    expect(money.transform('2500', 'INR')).toBe('₹2,500.00');
    expect(money.transform('0.00', 'INR')).toBe('₹0.00');
  });

  it('keeps digits a float would lose', () => {
    // 2^53 + 1 is not representable as a double (a float prints ...992); the string stays exact.
    expect(money.transform('9007199254740993.00', 'INR')).toBe('₹9,00,71,99,25,47,40,993.00');
  });

  it('shows the direction as + for money in and a real minus for money out', () => {
    expect(money.transform('45000.00', 'INR', 'CREDIT')).toBe('+₹45,000.00');
    expect(money.transform('2500.00', 'INR', 'DEBIT')).toBe('−₹2,500.00');
    expect(money.transform('-10.5', 'INR')).toBe('−₹10.50');
  });

  it('uses the currency of the amount', () => {
    expect(money.transform('1234.5', 'EUR')).toBe('€1,234.50');
  });

  it('shows nothing for missing or malformed amounts rather than a wrong number', () => {
    expect(money.transform(null, 'INR')).toBe('');
    expect(money.transform('100', null)).toBe('');
    expect(money.transform('1e5', 'INR')).toBe('');
    expect(money.transform('12,50', 'INR')).toBe('');
  });
});

describe('AccountNumberPipe', () => {
  const accountNumber = new AccountNumberPipe();

  it('groups the full number in fours so it can be read out and checked', () => {
    expect(accountNumber.transform('502148369012')).toBe('5021 4836 9012');
  });

  it('shows only the last 4 digits in lists', () => {
    expect(accountNumber.transform('502148369012', 'last4')).toBe('9012');
    expect(accountNumber.transform('5021 4836 9012', 'last4')).toBe('9012');
  });

  it('is empty for no number', () => {
    expect(accountNumber.transform(null)).toBe('');
  });
});
