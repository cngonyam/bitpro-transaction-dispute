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
  reviewerUsername?: string;
  approvedByUsername?: string;
  reversedByUsername?: string;
  reversedByRole?: string;
  reviewDecision?: string;
  approvalNote?: string;
  provisionalCreditIssued?: boolean;
  merchantEvidenceProvided?: boolean;
  decisionOutcome?: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DisputeHistoryEntry {
  id: number;
  disputeId: number;
  fromStatus?: string;
  toStatus?: string;
  action?: string;
  reasonCode?: string;
  actor?: string;
  actorRole?: string;
  changedAt?: string;
}

export interface ReviewDisputeRequest {
  decision: string;
  reviewNote?: string;
}

export interface ApproveDisputeRequest {
  approvalNote: string;
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

interface PageResponse<T> {
  content?: T[];
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
      this.http
        .get<PageResponse<Transaction>>(`${this.apiUrl}/transactions`, {
          headers: this.getHeaders(),
        })
        .pipe(map((response) => response?.content ?? [])),
    );
  }

  getAccounts(): Observable<AccountSummary[]> {
    return this.requestWithRetry(
      this.http
        .get<PageResponse<AccountSummary>>(`${this.apiUrl}/accounts`, {
          headers: this.getHeaders(),
        })
        .pipe(map((response) => response?.content ?? [])),
    );
  }

  createDispute(request: DisputeCreateRequest): Observable<Dispute> {
    const idempotencyKey = this.buildIdempotencyKey('create-dispute', {
      transactionReference: request.transactionReference,
      accountNumber: request.accountNumber,
      disputedAmount: request.disputedAmount,
      reasonCode: request.reasonCode,
      description: request.description,
      disputeType: request.disputeType,
    });

    return this.http.post<Dispute>(`${this.apiUrl}/disputes`, request, {
      headers: this.getHeaders().set('X-Idempotency-Key', idempotencyKey),
    });
  }

  getDisputes(): Observable<Dispute[]> {
    return this.requestWithRetry(
      this.http
        .get<PageResponse<Dispute>>(`${this.apiUrl}/disputes`, {
          headers: this.getHeaders(),
        })
        .pipe(map((response) => response?.content ?? [])),
    );
  }

  getDisputeHistory(disputeId: number): Observable<DisputeHistoryEntry[]> {
    return this.requestWithRetry(
      this.http.get<DisputeHistoryEntry[]>(`${this.apiUrl}/disputes/${disputeId}/history`, {
        headers: this.getHeaders(),
      }),
    );
  }

  reviewDispute(disputeId: number, decision: string, reviewNote?: string): Observable<Dispute> {
    return this.http.post<Dispute>(`${this.apiUrl}/disputes/${disputeId}/review`, {
      decision,
      reviewNote,
    }, {
      headers: this.getHeaders(),
    });
  }

  approveDispute(disputeId: number, approvalNote: string): Observable<Dispute> {
    return this.http.post<Dispute>(`${this.apiUrl}/disputes/${disputeId}/approve`, {
      approvalNote,
    }, {
      headers: this.getHeaders(),
    });
  }

  reverseDispute(disputeId: number, reasonCode: string): Observable<Dispute> {
    const idempotencyKey = this.buildIdempotencyKey('reverse-dispute', {
      disputeId,
      reasonCode,
    });

    return this.http.post<Dispute>(`${this.apiUrl}/disputes/${disputeId}/reverse`, { reasonCode }, {
      headers: this.getHeaders().set('X-Idempotency-Key', idempotencyKey),
    });
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

  private buildIdempotencyKey(prefix: string, payload: unknown): string {
    const normalized = JSON.stringify(payload ?? {});
    const hash = this.hashString(normalized);
    return `${prefix}-${hash}`;
  }

  private hashString(value: string): string {
    let hash = 2166136261;

    for (let index = 0; index < value.length; index += 1) {
      const character = value.charCodeAt(index);
      hash ^= character;
      hash = Math.imul(hash, 16777619);
    }

    return (hash >>> 0).toString(16).padStart(8, '0');
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
