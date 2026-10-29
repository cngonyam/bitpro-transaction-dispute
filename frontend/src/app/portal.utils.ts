import { AccountSummary, Transaction } from './transaction-dispute.service';

export type DashboardTab = 'transactions' | 'new-dispute' | 'track-dispute' | 'dispute-history' | 'profile';

const MESSAGE_KEYS = new Set(['message', 'error', 'errors', 'warning', 'warnings', 'detail', 'details', 'title']);

export function inferRoleForUsername(username: string | null | undefined): string {
  const normalized = (username ?? '').trim().toLowerCase();

  if (!normalized) {
    return 'CUSTOMER';
  }
  if (normalized.includes('admin')) {
    return 'ADMIN';
  }
  if (normalized.includes('lead')) {
    return 'LEAD';
  }
  if (normalized.includes('agent') || normalized.includes('operations') || normalized.includes('ops')) {
    return 'AGENT';
  }
  return 'CUSTOMER';
}

export function canReverseDisputes(profileRole: string | null | undefined, username: string | null | undefined): boolean {
  const privileged = ['AGENT', 'ADMIN', 'LEAD', 'OPERATIONS'];
  const roles = [(profileRole ?? '').toUpperCase(), inferRoleForUsername(username)];
  return roles.some((role) => privileged.some((allowed) => role.includes(allowed)));
}

export function extractApiMessages(payload: unknown): string[] {
  const messages = new Set<string>();

  const visit = (value: unknown): void => {
    if (value == null) {
      return;
    }
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (trimmed) {
        messages.add(trimmed);
      }
      return;
    }
    if (Array.isArray(value)) {
      value.forEach(visit);
      return;
    }
    if (typeof value !== 'object') {
      return;
    }
    Object.entries(value as Record<string, unknown>).forEach(([key, nested]) => {
      if (MESSAGE_KEYS.has(key)) {
        visit(nested);
      }
    });
  };

  visit(payload);
  return Array.from(messages);
}

export function resolveApiError(error: unknown, fallback: string): string {
  const httpError = error as { error?: unknown; message?: string } | null;
  const responseMessages = extractApiMessages(httpError?.error ?? httpError);

  if (responseMessages.length > 0) {
    return responseMessages[0];
  }
  if (typeof httpError?.error === 'string' && httpError.error.trim()) {
    return httpError.error.trim();
  }
  if (typeof httpError?.message === 'string' && httpError.message.trim()) {
    return httpError.message.trim();
  }
  return fallback;
}

export function isCreditTransaction(transaction?: Transaction | null): boolean {
  return (transaction?.type ?? '').toUpperCase() === 'CREDIT';
}

export function isDisputeDisabled(transaction?: Transaction | null): boolean {
  return isCreditTransaction(transaction) || (transaction?.status ?? '').toUpperCase() === 'REVERSED';
}

export function disputeProgress(status?: string): number {
  switch ((status ?? '').toUpperCase()) {
    case 'OPEN':
      return 25;
    case 'IN_REVIEW':
      return 60;
    case 'RESOLVED':
    case 'REVERSED':
      return 100;
    default:
      return 15;
  }
}

export function disputeTimeline(status?: string): string[] {
  switch ((status ?? '').toUpperCase()) {
    case 'RESOLVED':
      return ['Submitted', 'In review', 'Final review', 'Resolved'];
    case 'REVERSED':
      return ['Submitted', 'In review', 'Final review', 'Reversed'];
    case 'IN_REVIEW':
      return ['Submitted', 'In review', 'Awaiting decision'];
    default:
      return ['Submitted', 'Open', 'Under review'];
  }
}

/** CSS class used by the status badges, e.g. IN_REVIEW -> status-in-review. */
export function statusClass(status?: string): string {
  const normalized = (status ?? '').trim().toLowerCase().replace(/_/g, '-');
  return normalized ? `status-${normalized}` : '';
}

export function signedAmount(transaction: Transaction): number {
  const value = Number(transaction.amount ?? 0);
  return (transaction.type ?? '').toUpperCase() === 'DEBIT' ? -Math.abs(value) : Math.abs(value);
}

function sumSigned(transactions: Transaction[], status?: string): number {
  return transactions
    .filter((transaction) => !status || (transaction.status ?? '').toUpperCase() === status)
    .reduce((sum, transaction) => sum + signedAmount(transaction), 0);
}

export function pendingClearanceBalance(transactions: Transaction[]): number {
  return sumSigned(transactions, 'PENDING');
}

export function availableBalance(transactions: Transaction[], summary: AccountSummary | null): number {
  if (transactions.length > 0) {
    return sumSigned(transactions);
  }
  return summary ? Number(summary.availableBalance ?? 0) : 0;
}

export function accountBalance(transactions: Transaction[], summary: AccountSummary | null): number {
  if (transactions.length > 0) {
    return sumSigned(transactions, 'SETTLED');
  }
  if (summary && summary.balance != null) {
    return Number(summary.balance);
  }
  return availableBalance(transactions, summary) + pendingClearanceBalance(transactions);
}

export function formatAmount(value: number | string, type?: string): string {
  const numeric = Number(value);
  const signed = (type ?? '').toUpperCase() === 'DEBIT' ? -Math.abs(numeric) : numeric;

  return new Intl.NumberFormat('en-ZA', {
    style: 'currency',
    currency: 'ZAR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(signed);
}

export function formatDate(value?: string): string {
  return value ? new Date(value).toLocaleString() : '—';
}
