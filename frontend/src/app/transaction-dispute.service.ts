import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map, retry, timer } from 'rxjs';

export interface Transaction {
  id: number;
  transactionReference: string;
  accountNumber: string;
  accountBalance?: number;
  amount: number;
  currency: string;
  type: string;
  merchantName: string;
  status: string;
  transactionDate: string;
  description?: string;
}

export interface DisputeCreateRequest {
  transactionReference: string;
  accountNumber: string;
  disputedAmount: number;
  reasonCode: string;
  disputeType: 'CHARGEBACK';
  description: string;
  provisionalCreditIssued?: boolean;
  merchantEvidenceProvided?: boolean;
  decisionOutcome?: string;
}

export interface Dispute {
  id: number;
  disputeId: string;
  transactionReference: string;
  accountNumber: string;
  disputedAmount: number;
  reasonCode: string;
  disputeType?: 'CHARGEBACK';
  description?: string;
  assignedToFullName?: string;
  provisionalCreditIssued?: boolean;
  merchantEvidenceProvided?: boolean;
  decisionOutcome?: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ReverseDisputeRequest {
  reasonCode: string;
}

export interface AuthResponse {
  token: string;
  username: string;
}

export interface AccountSummary {
  id: number;
  accountNumber: string;
  customerName: string;
  currency: string;
  availableBalance: number;
  balance?: number;
  status: string;
}

export interface UserProfile {
  id?: number;
  username?: string;
  fullName: string;
  role: string;
  email: string;
  phone: string;
  department: string;
  region: string;
}

@Injectable({ providedIn: 'root' })
export class TransactionDisputeService {
  private readonly apiUrl = '/api/v1';
  private authToken = '';

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<AuthResponse> {
    const cleanedUsername = (username ?? '').trim();

    return this.requestWithRetry(
      this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, {
        username: cleanedUsername,
        password: password ?? '',
      }),
    );
  }

  setToken(token: string): void {
    this.authToken = token ?? '';
  }

  clearToken(): void {
    this.authToken = '';
  }

  private getHeaders(): HttpHeaders {
    if (!this.authToken) {
      return new HttpHeaders();
    }

    return new HttpHeaders({
      Authorization: `Bearer ${this.authToken}`,
    });
  }

  getTransactions(): Observable<Transaction[]> {
    return this.requestWithRetry(
      this.http.get<Transaction[]>(`${this.apiUrl}/transactions`, {
        headers: this.getHeaders(),
      }),
    );
  }

  getAccounts(): Observable<AccountSummary[]> {
    return this.requestWithRetry(
      this.http.get<AccountSummary[]>(`${this.apiUrl}/accounts`, {
        headers: this.getHeaders(),
      }),
    );
  }

  createDispute(request: DisputeCreateRequest): Observable<Dispute> {
    return this.requestWithRetry(
      this.http.post<Dispute>(`${this.apiUrl}/disputes`, request, {
        headers: this.getHeaders(),
      }),
    );
  }

  getDisputes(): Observable<Dispute[]> {
    return this.requestWithRetry(
      this.http.get<Dispute[]>(`${this.apiUrl}/disputes`, {
        headers: this.getHeaders(),
      }),
    );
  }

  reverseDispute(disputeId: number, reasonCode: string): Observable<Dispute> {
    return this.requestWithRetry(
      this.http.post<Dispute>(`${this.apiUrl}/disputes/${disputeId}/reverse`, { reasonCode }, {
        headers: this.getHeaders(),
      }),
    );
  }

  getProfile(): Observable<UserProfile> {
    return this.requestWithRetry(
      this.http.get<UserProfile>(`${this.apiUrl}/profile`, {
        headers: this.getHeaders(),
      }),
    );
  }

  updateProfile(profile: UserProfile): Observable<UserProfile> {
    return this.requestWithRetry(
      this.http.put<UserProfile>(`${this.apiUrl}/profile`, profile, {
        headers: this.getHeaders(),
      }),
    );
  }

  private requestWithRetry<T>(request$: Observable<T>): Observable<T> {
    return request$.pipe(
      retry({
        count: 1,
        delay: () => timer(200),
      }),
    );
  }
}
