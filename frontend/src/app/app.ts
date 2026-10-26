import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AccountSummary, Dispute, DisputeCreateRequest, Transaction, TransactionDisputeService, UserProfile } from './transaction-dispute.service';

export type DashboardTab = 'transactions' | 'new-dispute' | 'track-dispute' | 'dispute-history' | 'profile';

interface LoginForm {
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
  reverseReason = '';
  historyFilter: 'ALL' | 'OPEN' | 'IN_REVIEW' | 'RESOLVED' = 'ALL';
  isSubmitting = false;
  isReversingDispute = false;
  isLoggingIn = false;
  isLoggedIn = false;
  successMessage = '';
  errorMessage = '';

  profile: UserProfile = {
    fullName: 'Ndumiso Ngonyama',
    role: 'Dispute Operations Lead',
    email: 'ndumiso.ngonyama@bitpro.co.za',
    phone: '+27 11 555 0123',
    department: 'Customer Operations',
    region: 'Johannesburg, South Africa',
  };

  loginForm: LoginForm = {
    username: 'dev-user',
    password: 'dev-pass123',
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

  ngOnInit(): void {
    this.autoLogin();
  }

  private autoLogin(): void {
    this.loginForm.username = 'dev-user';
    this.loginForm.password = 'dev-pass123';
    this.login();
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
          this.isLoggedIn = true;
          this.successMessage = `Welcome back, ${response.username}.`;

          this.refreshDashboard();
        },
        error: () => {
          this.isLoggedIn = false;
          this.api.clearToken();
          this.errorMessage = 'Invalid username or password. Please try the dev credentials.';
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
      error: () => {
        this.errorMessage = 'Unable to load transactions right now.';
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
      error: () => {
        this.errorMessage = 'Unable to load the portal data right now.';
      },
    });
  }

  private loadProfile(): void {
    this.api.getProfile().subscribe({
      next: (profile) => {
        this.profile = { ...this.profile, ...profile };
      },
      error: () => {
        this.profile = {
          fullName: 'Ndumiso Ngonyama',
          role: 'Dispute Operations Lead',
          email: 'ndumiso.ngonyama@bitpro.co.za',
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
      error: () => {
        this.accountSummary = null;
      },
    });
  }

  logout(): void {
    this.api.clearToken();
    this.isLoggedIn = false;
    this.transactions = [];
    this.disputes = [];
    this.selectedDispute = null;
    this.selectedTransaction = null;
    this.successMessage = 'You have been logged out.';
    this.errorMessage = '';
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
    this.activeTab = 'track-dispute';
  }

  reverseDispute(): void {
    if (!this.selectedDispute) {
      this.errorMessage = 'Please select a dispute to reverse.';
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
        this.refreshDashboard();
      },
      error: () => {
        this.isReversingDispute = false;
        this.errorMessage = 'Unable to reverse this dispute. Please try again.';
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
          error: () => {
            this.selectedDispute = createdDispute ?? this.selectedDispute;
            this.loadAccountSummary();
            this.loadTransactions();
            this.activeTab = 'track-dispute';
            this.cdr.detectChanges();
          },
        });
      },
      error: () => {
        this.isSubmitting = false;
        this.errorMessage = 'The dispute could not be submitted. Please check the form and try again.';
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
      error: () => {
        this.errorMessage = 'The profile could not be saved right now.';
      },
    });
  }

  isCreditTransaction(transaction?: Transaction | null): boolean {
    return (transaction?.type ?? '').toUpperCase() === 'CREDIT';
  }

  getDisputeProgress(status?: string): number {
    const normalized = (status ?? '').toUpperCase();

    switch (normalized) {
      case 'OPEN':
        return 25;
      case 'IN_REVIEW':
        return 60;
      case 'RESOLVED':
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
