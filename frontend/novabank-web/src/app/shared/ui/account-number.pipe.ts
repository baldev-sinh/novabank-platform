import { Pipe, PipeTransform } from '@angular/core';

/**
 * Account numbers for display. `full` groups the digits in fours (`5021 4836 9012`) so they can be
 * read out and checked; `last4` is what lists show ("Account ending 9012"), so a full number appears
 * only where the customer needs it.
 */
@Pipe({ name: 'nbAccountNumber' })
export class AccountNumberPipe implements PipeTransform {
  transform(value: string | null | undefined, format: 'full' | 'last4' = 'full'): string {
    if (!value) {
      return '';
    }
    const digits = value.replace(/\s/g, '');
    if (format === 'last4') {
      return digits.slice(-4);
    }
    return digits.replace(/(\d{4})(?=\d)/g, '$1 ');
  }
}
