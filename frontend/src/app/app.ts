import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AccountSummary, Dispute, DisputeCreateRequest, DisputeHistoryEntry, Transaction, TransactionDisputeService, UserProfile } from './transaction-dispute.service';

export type DashboardTab = 'transactions' | 'new-dispute' | 'track-dispute' | 'dispute-history' | 'profile';

interface LoginForm {
  username: string;
  password: string;
}

interface DemoUserOption {
  label: string;
  username: string;
  password: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  activeTab: DashboardTab = 'transactions';
  transactions: Transaction[] = [];
  disputes: Dispute[] = [];
  accountSummary: AccountSummary | null = null;
  selectedTransaction: Transaction | null = null;
  selectedDispute: Dispute | null = null;
  disputeHistory: DisputeHistoryEntry[] = [];
  reverseReason = '';
  reviewDecision = 'APPROVE';
  reviewNote = '';
  approvalNote = '';
  historyFilter: 'ALL' | 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'REVERSED' = 'ALL';
  isSubmitting = false;
  isReviewingDispute = false;
  isApprovingDispute = false;
  isReversingDispute = false;
  isLoggingIn = false;
  isLoggedIn = false;
  successMessage = '';
  errorMessage = '';

  profile: UserProfile = {
    fullName: 'Dispute Operations Lead',
    role: 'AGENT',
    email: 'lead-user@bitpro.co.za',
    phone: '+27 11 555 0123',
    department: 'Customer Operations',
    region: 'Johannesburg, South Africa',
  };

  demoUsers: DemoUserOption[] = [
    { label: 'Admin', username: 'admin-user', password: 'admin-pass123' },
    { label: 'Agent', username: 'agent-user', password: 'agent-pass123' },
    { label: 'Lead', username: 'lead-user', password: 'lead-pass123' },
    { label: 'Customer', username: 'customer-user', password: 'customer-pass123' },
  ];

  loginForm: LoginForm = {
    username: 'lead-user',
    password: 'lead-pass123',
  };

  form: DisputeCreateRequest = {
    transactionReference: '',
    accountNumber: '',
    disputedAmount: 0,
    reasonCode: '',
    disputeType: 'CHARGEBACK',
    description: '',
    provisionalCreditIssued: false,
    merchantEvidenceProvided: false,
    decisionOutcome: '',
  };

  constructor(
    private api: TransactionDisputeService,
    private cdr: ChangeDetectorRef,
  ) {}

  private inferRoleForUsername(username: string): string {
    const normalized = (username ?? '').trim().toLowerCase();

    if (!normalized) {
      return 'CUSTOMER';
    }

    if (normalized.includes('admin')) {
      return 'ADMIN';
    }

    if (normalized.includes('agent') || normalized.includes('lead') || normalized.includes('operations') || normalized.includes('ops')) {
      return 'AGENT';
    }

    return 'CUSTOMER';
  }

  private canReverseDisputes(): boolean {
    const profileRole = (this.profile?.role ?? '').toUpperCase();
    const loginRole = this.inferRoleForUsername(this.loginForm.username).toUpperCase();

    return profileRole.includes('AGENT')
      || profileRole.includes('ADMIN')
      || profileRole.includes('LEAD')
      || profileRole.includes('OPERATIONS')
      || loginRole.includes('AGENT')
      || loginRole.includes('ADMIN')
      || loginRole.includes('LEAD')
      || loginRole.includes('OPERATIONS');
  }

  private extractApiMessages(payload: unknown): string[] {
    const messages = new Set<string>();
    const keysToInspect = new Set(['message', 'error', 'errors', 'warning', 'warnings', 'detail', 'details', 'title']);

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

      Object.entries(value as Record<string, unknown>).forEach(([key, nestedValue]) => {
        if (keysToInspect.has(key)) {
          visit(nestedValue);
        }
      });
    };

    visit(payload);
    return Array.from(messages);
  }

  private resolveApiError(error: unknown, fallback: string): string {
    const httpError = error as HttpErrorResponse | null;
    const responseMessages = this.extractApiMessages(httpError?.error ?? httpError);

    if (responseMessages.length > 0) {
      return responseMessages[0];
    }

    const rawMessage = httpError?.error?.message ?? httpError?.error?.error ?? httpError?.message ?? '';

    if (typeof rawMessage === 'string' && rawMessage.trim()) {
      return rawMessage.trim();
    }

    if (typeof httpError?.error === 'string' && httpError.error.trim()) {
      return httpError.error.trim();
    }

    return fallback;
  }

  selectDemoUser(option: DemoUserOption): void {
    this.loginForm = {
      username: option.username,
      password: option.password,
    };
    this.errorMessage = '';
    this.successMessage = `Demo user ready: ${option.label}.`;
  }

  ngOnInit(): void {
    this.api.clearToken();
    this.isLoggedIn = false;
  }

  login(): void {
    const username = this.loginForm.username.trim();
    const password = this.loginForm.password;

    if (this.isLoggingIn) {
      return;
    }

    if (!username || !password) {
      this.errorMessage = 'Please enter both username and password.';
      return;
    }

    this.isLoggingIn = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.api.login(username, password)
      .pipe(finalize(() => {
        this.isLoggingIn = false;
      }))
      .subscribe({
        next: (response) => {
          this.api.setToken(response.token);
          this.profile = {
            ...this.profile,
            role: this.inferRoleForUsername(response.username),
          };
          this.isLoggedIn = true;
          this.successMessage = `Welcome back, ${response.username}.`;

          this.refreshDashboard();
        },
        error: (error) => {
          this.isLoggedIn = false;
          this.api.clearToken();
          this.errorMessage = this.resolveApiError(error, 'Invalid username or password. Please try the dev credentials.');
        },
      });
  }

  private refreshDashboard(): void {
    this.loadProfile();
    this.loadAccountSummary();

    this.api.getTransactions().subscribe({
      next: (transactions) => {
        this.transactions = transactions;
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.errorMessage = this.resolveApiError(error, 'Unable to load transactions right now.');
      },
    });

    this.api.getDisputes().subscribe({
      next: (disputes) => {
        this.disputes = disputes;
        if (!this.selectedDispute && disputes.length > 0) {
          this.selectedDispute = disputes[0];
        }
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.errorMessage = this.resolveApiError(error, 'Unable to load the portal data right now.');
      },
    });
  }

  private loadProfile(): void {
    this.api.getProfile().subscribe({
      next: (profile) => {
        this.profile = {
          ...this.profile,
          ...profile,
          role: (profile?.role ?? this.inferRoleForUsername(this.loginForm.username)).toUpperCase(),
        };
      },
      error: (error) => {
        this.errorMessage = this.resolveApiError(error, 'Unable to load the current profile.');
        this.profile = {
          fullName: 'Dispute Operations Lead',
          role: 'AGENT',
          email: 'lead-user@bitpro.co.za',
          phone: '+27 11 555 0123',
          department: 'Customer Operations',
          region: 'Johannesburg, South Africa',
        };
      },
    });
  }

  private loadAccountSummary(): void {
    this.api.getAccounts().subscribe({
      next: (accounts) => {
        this.accountSummary = accounts[0] ?? null;
      },
      error: (error) => {
        this.accountSummary = null;
        this.errorMessage = this.resolveApiError(error, 'Unable to load the account summary.');
      },
    });
  }

  logout(): void {
    this.api.clearToken();
    this.isLoggedIn = false;
    this.activeTab = 'transactions';
    this.transactions = [];
    this.disputes = [];
    this.selectedDispute = null;
    this.selectedTransaction = null;
    this.successMessage = 'You have been logged out.';
    this.errorMessage = '';
    this.loginForm = { username: '', password: '' };
  }

  get filteredDisputes(): Dispute[] {
    if (this.historyFilter === 'ALL') {
      return this.disputes;
    }

    return this.disputes.filter((dispute) => dispute.status.toUpperCase() === this.historyFilter);
  }

  get openDisputeCount(): number {
    return this.disputes.filter((dispute) => dispute.status.toUpperCase() === 'OPEN').length;
  }

  get inReviewDisputeCount(): number {
    return this.disputes.filter((dispute) => dispute.status.toUpperCase() === 'IN_REVIEW').length;
  }

  get resolvedDisputeCount(): number {
    return this.disputes.filter((dispute) => dispute.status.toUpperCase() === 'RESOLVED').length;
  }

  loadTransactions(): void {
    this.api.getTransactions().subscribe({
      next: (data) => {
        this.transactions = data;
      },
      error: () => {
        this.errorMessage = 'Unable to load transactions right now.';
      },
    });
  }

  loadDisputes(): void {
    this.api.getDisputes().subscribe({
      next: (data) => {
        this.disputes = data;
        if (!this.selectedDispute && data.length > 0) {
          this.selectedDispute = data[0];
        }
      },
      error: () => {
        this.errorMessage = 'Unable to load disputes right now.';
      },
    });
  }

  setActiveTab(tab: DashboardTab): void {
    this.activeTab = tab;
    this.errorMessage = '';
    this.successMessage = '';
  }

  selectTransaction(transaction: Transaction): void {
    if (this.isCreditTransaction(transaction)) {
      this.errorMessage = 'Credit transactions are not eligible for dispute submission.';
      this.successMessage = '';
      return;
    }

    if (this.isDisputeDisabled(transaction)) {
      this.errorMessage = 'Reversed transactions are not eligible for dispute submission.';
      this.successMessage = '';
      return;
    }

    const absoluteAmount = Math.abs(Number(transaction.amount ?? 0));
    this.selectedTransaction = transaction;
    this.form.transactionReference = transaction.transactionReference;
    this.form.accountNumber = transaction.accountNumber;
    this.form.disputedAmount = absoluteAmount;
    this.form.reasonCode = 'MERCHANT_ERROR';
    this.form.disputeType = 'CHARGEBACK';
    this.form.description = `Dispute for ${transaction.merchantName}`;
    this.form.provisionalCreditIssued = false;
    this.form.merchantEvidenceProvided = false;
    this.form.decisionOutcome = '';
    this.errorMessage = '';
    this.successMessage = 'Transaction selected. Please review and submit the dispute details.';
    this.activeTab = 'new-dispute';
  }

  selectDisputeToTrack(dispute: Dispute): void {
    this.selectedDispute = dispute;
    this.reverseReason = dispute.reasonCode || 'MERCHANT_ERROR';
    this.reviewDecision = dispute.reviewDecision || 'APPROVE';
    this.reviewNote = dispute.reviewDecision ? `Reviewed: ${dispute.reviewDecision}` : '';
    this.approvalNote = dispute.approvalNote || '';
    this.disputeHistory = [];
    this.activeTab = 'track-dispute';

    this.api.getDisputeHistory(dispute.id).subscribe({
      next: (history) => {
        this.disputeHistory = history ?? [];
      },
      error: () => {
        this.disputeHistory = [];
      },
    });
  }

  reviewDisputeAction(): void {
    if (!this.selectedDispute) {
      this.errorMessage = 'Please select a dispute to review.';
      return;
    }

    if (this.isReviewingDispute) {
      return;
    }

    const decision = (this.reviewDecision ?? 'APPROVE').trim() || 'APPROVE';
    const note = (this.reviewNote ?? '').trim() || 'Reviewed by operations team.';

    this.isReviewingDispute = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.api.reviewDispute(this.selectedDispute.id, decision, note).subscribe({
      next: (updatedDispute) => {
        this.selectedDispute = updatedDispute;
        this.reviewDecision = updatedDispute.reviewDecision || decision;
        this.reviewNote = updatedDispute.reviewDecision ? `Reviewed: ${updatedDispute.reviewDecision}` : '';
        this.isReviewingDispute = false;
        this.successMessage = `Dispute ${updatedDispute.disputeId} has been marked for review.`;
        this.refreshDashboard();
      },
      error: (error) => {
        this.isReviewingDispute = false;
        this.errorMessage = this.resolveApiError(error, 'Unable to review this dispute right now.');
      },
    });
  }

  approveDisputeAction(): void {
    if (!this.selectedDispute) {
      this.errorMessage = 'Please select a dispute to approve.';
      return;
    }

    if (this.isApprovingDispute) {
      return;
    }

    const note = (this.approvalNote ?? '').trim() || 'Approved by dispute operations lead.';

    this.isApprovingDispute = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.api.approveDispute(this.selectedDispute.id, note).subscribe({
      next: (updatedDispute) => {
        this.selectedDispute = updatedDispute;
        this.approvalNote = updatedDispute.approvalNote || note;
        this.isApprovingDispute = false;
        this.successMessage = `Dispute ${updatedDispute.disputeId} has been approved and provisional credit issued.`;
        this.refreshDashboard();
      },
      error: (error) => {
        this.isApprovingDispute = false;
        this.errorMessage = this.resolveApiError(error, 'Unable to approve this dispute right now.');
      },
    });
  }

  reverseDispute(): void {
    if (!this.selectedDispute) {
      this.errorMessage = 'Please select a dispute to reverse.';
      return;
    }

    if (!this.canReverseDisputes()) {
      this.errorMessage = 'Only authorised dispute agents or administrators can reverse a dispute.';
      return;
    }

    const reason = (this.reverseReason ?? '').trim();
    if (!reason) {
      this.errorMessage = 'Please choose a reversal reason before resolving the dispute.';
      return;
    }

    if (this.isReversingDispute) {
      return;
    }

    this.isReversingDispute = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.api.reverseDispute(this.selectedDispute.id, reason).subscribe({
      next: (updatedDispute) => {
        this.selectedDispute = updatedDispute;
        this.reverseReason = updatedDispute.reasonCode || reason;
        this.isReversingDispute = false;
        this.successMessage = `Dispute ${updatedDispute.disputeId} has been reversed and resolved.`;

        const responseWarnings = this.extractApiMessages(updatedDispute);
        if (responseWarnings.length > 0) {
          this.errorMessage = responseWarnings.join('. ');
        } else {
          this.errorMessage = '';
        }

        this.refreshDashboard();
      },
      error: (error) => {
        this.isReversingDispute = false;
        this.errorMessage = this.resolveApiError(error, 'Unable to reverse this dispute. Please try again.');
      },
    });
  }

  submitDispute(): void {
    if (this.isSubmitting) {
      return;
    }

    const transactionReference = (this.form.transactionReference ?? '').trim();
    const accountNumber = (this.form.accountNumber ?? '').trim();
    const disputeType = (this.form.disputeType ?? 'CHARGEBACK').toUpperCase();
    const reasonCode = (this.form.reasonCode ?? '').trim();

    if (!transactionReference || !accountNumber || !reasonCode || !disputeType) {
      this.errorMessage = 'Please select a transaction and complete the dispute type and details.';
      return;
    }

    const selectedTransaction = this.transactions.find(
      (transaction) => transaction.transactionReference === transactionReference,
    ) ?? this.selectedTransaction;

    if (!selectedTransaction) {
      this.errorMessage = 'Please select a valid transaction before submitting the dispute.';
      return;
    }

    if (this.isCreditTransaction(selectedTransaction)) {
      this.errorMessage = 'Credit transactions cannot be disputed.';
      return;
    }

    const normalizedPayload: DisputeCreateRequest = {
      ...this.form,
      transactionReference,
      accountNumber,
      disputeType: 'CHARGEBACK',
      reasonCode,
      description: (this.form.description ?? '').trim() || `Dispute for ${selectedTransaction.merchantName}`,
      provisionalCreditIssued: Boolean(this.form.provisionalCreditIssued),
      merchantEvidenceProvided: Boolean(this.form.merchantEvidenceProvided),
      decisionOutcome: this.form.decisionOutcome?.trim() ?? '',
    };

    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.api.createDispute(normalizedPayload).subscribe({
      next: (createdDispute) => {
        this.isSubmitting = false;
        this.successMessage = 'Dispute submitted successfully.';
        this.form = {
          transactionReference: '',
          accountNumber: '',
          disputedAmount: 0,
          reasonCode: '',
          disputeType: 'CHARGEBACK',
          description: '',
          provisionalCreditIssued: false,
          merchantEvidenceProvided: false,
          decisionOutcome: '',
        };
        this.selectedTransaction = null;

        this.api.getDisputes().subscribe({
          next: (data) => {
            this.disputes = data;
            this.selectedDispute = data.find((dispute) => dispute.disputeId === createdDispute?.disputeId)
              ?? createdDispute ?? data[0] ?? null;
            this.loadAccountSummary();
            this.loadTransactions();
            this.activeTab = 'track-dispute';
            this.cdr.detectChanges();
          },
          error: (error) => {
            this.selectedDispute = createdDispute ?? this.selectedDispute;
            this.errorMessage = this.resolveApiError(error, 'Unable to reload the updated dispute list.');
            this.loadAccountSummary();
            this.loadTransactions();
            this.activeTab = 'track-dispute';
            this.cdr.detectChanges();
          },
        });
      },
      error: (error) => {
        this.isSubmitting = false;
        this.errorMessage = this.resolveApiError(error, 'The dispute could not be submitted. Please check the form and try again.');
      },
    });
  }

  updateProfile(): void {
    this.api.updateProfile(this.profile).subscribe({
      next: (updatedProfile) => {
        this.profile = updatedProfile;
        this.successMessage = 'Profile updated successfully.';
        this.errorMessage = '';
      },
      error: (error) => {
        this.errorMessage = this.resolveApiError(error, 'The profile could not be saved right now.');
      },
    });
  }

  isCreditTransaction(transaction?: Transaction | null): boolean {
    return (transaction?.type ?? '').toUpperCase() === 'CREDIT';
  }

  isDisputeDisabled(transaction?: Transaction | null): boolean {
    return this.isCreditTransaction(transaction)
      || (transaction?.status ?? '').toUpperCase() === 'REVERSED';
  }

  getDisputeProgress(status?: string): number {
    const normalized = (status ?? '').toUpperCase();

    switch (normalized) {
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

  getDisputeTimeline(status?: string): string[] {
    const normalized = (status ?? '').toUpperCase();

    if (normalized === 'RESOLVED') {
      return ['Submitted', 'In review', 'Final review', 'Resolved'];
    }

    if (normalized === 'REVERSED') {
      return ['Submitted', 'In review', 'Final review', 'Reversed'];
    }

    if (normalized === 'IN_REVIEW') {
      return ['Submitted', 'In review', 'Awaiting decision'];
    }

    return ['Submitted', 'Open', 'Under review'];
  }

  private getSignedTransactionAmount(transaction: Transaction): number {
    const numericValue = Number(transaction.amount ?? 0);
    return (transaction.type ?? '').toUpperCase() === 'DEBIT'
      ? -Math.abs(numericValue)
      : Math.abs(numericValue);
  }

  private getSettledTotal(): number {
    return this.transactions
      .filter((transaction) => (transaction.status ?? '').toUpperCase() === 'SETTLED')
      .reduce((sum, transaction) => sum + this.getSignedTransactionAmount(transaction), 0);
  }

  private getAllTransactionsTotal(): number {
    return this.transactions.reduce((sum, transaction) => sum + this.getSignedTransactionAmount(transaction), 0);
  }

  getAvailableBalance(): number {
    if (this.transactions.length > 0) {
      return this.getAllTransactionsTotal();
    }

    if (this.accountSummary) {
      return Number(this.accountSummary.availableBalance ?? 0);
    }

    return 0;
  }

  getAccountBalance(): number {
    if (this.transactions.length > 0) {
      return this.getSettledTotal();
    }

    if (this.accountSummary && this.accountSummary.balance != null) {
      return Number(this.accountSummary.balance ?? 0);
    }

    const availableBalance = this.getAvailableBalance();
    const pendingClearance = this.getPendingClearanceBalance();

    return availableBalance + pendingClearance;
  }

  getPendingClearanceBalance(): number {
    return this.transactions
      .filter((transaction) => transaction.status?.toUpperCase() === 'PENDING')
      .reduce((sum, transaction) => {
        const numericValue = Number(transaction.amount ?? 0);
        const signedValue = (transaction.type ?? '').toUpperCase() === 'DEBIT'
          ? -Math.abs(numericValue)
          : numericValue;
        return sum + signedValue;
      }, 0);
  }

  formatAmount(value: number | string, type?: string): string {
    const numericValue = Number(value);
    const isDebitTransaction = (type ?? '').toUpperCase() === 'DEBIT';
    const signedValue = isDebitTransaction ? -Math.abs(numericValue) : numericValue;

    return new Intl.NumberFormat('en-ZA', {
      style: 'currency',
      currency: 'ZAR',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(signedValue);
  }

  formatDate(value?: string): string {
    if (!value) {
      return '—';
    }

    return new Date(value).toLocaleString();
  }
}
