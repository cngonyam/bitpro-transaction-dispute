import {
  accountBalance,
  availableBalance,
  canReverseDisputes,
  disputeProgress,
  disputeTimeline,
  extractApiMessages,
  formatDate,
  inferRoleForUsername,
  isCreditTransaction,
  isDisputeDisabled,
  pendingClearanceBalance,
  resolveApiError,
  signedAmount,
  statusClass,
} from './portal.utils';
import { creditTransaction, debitTransaction, reversedTransaction } from './testing/test-data';

describe('portal utils', () => {
  describe('roles', () => {
    it('infers the role from the username', () => {
      expect(inferRoleForUsername('admin-user')).toBe('ADMIN');
      expect(inferRoleForUsername('Lead-User')).toBe('LEAD');
      expect(inferRoleForUsername('agent-user')).toBe('AGENT');
      expect(inferRoleForUsername('ops-bob')).toBe('AGENT');
      expect(inferRoleForUsername('customer-user')).toBe('CUSTOMER');
      expect(inferRoleForUsername('  ')).toBe('CUSTOMER');
      expect(inferRoleForUsername(null)).toBe('CUSTOMER');
    });

    it('only lets privileged roles reverse disputes', () => {
      expect(canReverseDisputes('AGENT', 'customer-user')).toBe(true);
      expect(canReverseDisputes('CUSTOMER', 'lead-user')).toBe(true);
      expect(canReverseDisputes('CUSTOMER', 'customer-user')).toBe(false);
      expect(canReverseDisputes(undefined, undefined)).toBe(false);
    });
  });

  describe('API errors', () => {
    it('collects de-duplicated messages from nested payloads', () => {
      const payload = { message: ' Boom ', errors: ['a', 'a', { detail: 'b' }], ignored: 'x' };
      expect(extractApiMessages(payload)).toEqual(['Boom', 'a', 'b']);
      expect(extractApiMessages(null)).toEqual([]);
      expect(extractApiMessages(42)).toEqual([]);
    });

    it('prefers the response body, then the error message, then the fallback', () => {
      expect(resolveApiError({ error: { message: 'Conflict' } }, 'fallback')).toBe('Conflict');
      expect(resolveApiError({ error: 'plain text' }, 'fallback')).toBe('plain text');
      expect(resolveApiError({ message: 'Http failure' }, 'fallback')).toBe('Http failure');
      expect(resolveApiError(null, 'fallback')).toBe('fallback');
    });
  });

  describe('transactions', () => {
    it('identifies disputable transactions', () => {
      expect(isCreditTransaction(creditTransaction)).toBe(true);
      expect(isDisputeDisabled(creditTransaction)).toBe(true);
      expect(isDisputeDisabled(reversedTransaction)).toBe(true);
      expect(isDisputeDisabled(debitTransaction)).toBe(false);
      expect(isDisputeDisabled(null)).toBe(false);
    });

    it('signs amounts by transaction type', () => {
      expect(signedAmount(debitTransaction)).toBe(-100);
      expect(signedAmount(creditTransaction)).toBe(40);
    });

    it('calculates balances from transactions, falling back to the account summary', () => {
      const pending = { ...debitTransaction, id: 9, status: 'PENDING', amount: 10 };
      const all = [debitTransaction, creditTransaction, pending];
      const summary = { id: 1, accountNumber: 'A', customerName: 'C', currency: 'ZAR', availableBalance: 500, balance: 700, status: 'ACTIVE' };

      expect(availableBalance(all, null)).toBe(-70);
      expect(accountBalance(all, null)).toBe(-60);
      expect(pendingClearanceBalance(all)).toBe(-10);

      expect(availableBalance([], summary)).toBe(500);
      expect(accountBalance([], summary)).toBe(700);
      expect(availableBalance([], null)).toBe(0);
      expect(accountBalance([], { ...summary, balance: undefined })).toBe(500);
    });
  });

  describe('dispute presentation', () => {
    it('maps status to progress and timeline', () => {
      expect(disputeProgress('OPEN')).toBe(25);
      expect(disputeProgress('in_review')).toBe(60);
      expect(disputeProgress('RESOLVED')).toBe(100);
      expect(disputeProgress('REVERSED')).toBe(100);
      expect(disputeProgress('UNKNOWN')).toBe(15);
      expect(disputeTimeline('RESOLVED')).toEqual(['Submitted', 'In review', 'Final review', 'Resolved']);
      expect(disputeTimeline('REVERSED')[3]).toBe('Reversed');
      expect(disputeTimeline('IN_REVIEW')).toHaveLength(3);
      expect(disputeTimeline(undefined)).toEqual(['Submitted', 'Open', 'Under review']);
    });

    it('builds css classes from statuses', () => {
      expect(statusClass('PROVISIONAL_CREDIT_ISSUED')).toBe('status-provisional-credit-issued');
      expect(statusClass('OPEN')).toBe('status-open');
      expect(statusClass(undefined)).toBe('');
    });

    it('formats missing dates as a dash', () => {
      expect(formatDate(undefined)).toBe('—');
      expect(formatDate('2026-10-01T00:00:00')).not.toBe('—');
    });
  });
});
