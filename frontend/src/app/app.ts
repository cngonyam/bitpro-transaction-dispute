import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { Dispute, DisputeCreateRequest, Transaction, TransactionDisputeService, UserProfile } from './transaction-dispute.service';

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
  selectedTransaction: Transaction | null = null;
  selectedDispute: Dispute | null = null;
  historyFilter: 'ALL' | 'OPEN' | 'IN_REVIEW' | 'RESOLVED' = 'ALL';
  isSubmitting = false;
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
    description: '',
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

          this.loadProfile();

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
        },
        error: () => {
          this.isLoggedIn = false;
          this.api.clearToken();
          this.errorMessage = 'Invalid username or password. Please try the dev credentials.';
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
    this.selectedTransaction = transaction;
    this.form.transactionReference = transaction.transactionReference;
    this.form.accountNumber = transaction.accountNumber;
    this.form.disputedAmount = transaction.amount;
    this.form.reasonCode = 'MERCHANT_ERROR';
    this.form.description = `Dispute for ${transaction.merchantName}`;
    this.activeTab = 'new-dispute';
    this.errorMessage = '';
    this.successMessage = '';
  }

  selectDisputeToTrack(dispute: Dispute): void {
    this.selectedDispute = dispute;
    this.activeTab = 'track-dispute';
  }

  submitDispute(): void {
    if (!this.form.transactionReference || !this.form.accountNumber || !this.form.reasonCode) {
      this.errorMessage = 'Please select a transaction and complete the dispute details.';
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.api.createDispute(this.form).subscribe({
      next: (createdDispute) => {
        this.isSubmitting = false;
        this.successMessage = 'Dispute submitted successfully.';
        this.form = {
          transactionReference: '',
          accountNumber: '',
          disputedAmount: 0,
          reasonCode: '',
          description: '',
        };
        this.selectedTransaction = null;

        this.api.getDisputes().subscribe({
          next: (data) => {
            this.disputes = data;
            this.selectedDispute = data.find((dispute) => dispute.disputeId === createdDispute?.disputeId)
              ?? createdDispute ?? data[0] ?? null;
            this.activeTab = 'track-dispute';
            this.cdr.detectChanges();
          },
          error: () => {
            this.selectedDispute = createdDispute ?? this.selectedDispute;
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

  formatAmount(value: number | string): string {
    return new Intl.NumberFormat('en-ZA', {
      style: 'currency',
      currency: 'ZAR',
    }).format(Number(value));
  }

  formatDate(value?: string): string {
    if (!value) {
      return '—';
    }

    return new Date(value).toLocaleString();
  }
}
