import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';

export interface Transaction {
  id: number;
  transactionReference: string;
  accountNumber: string;
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
  description: string;
}

export interface Dispute {
  id: number;
  disputeId: string;
  transactionReference: string;
  accountNumber: string;
  disputedAmount: number;
  reasonCode: string;
  description?: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AuthResponse {
  token: string;
  username: string;
}

@Injectable({ providedIn: 'root' })
export class TransactionDisputeService {
  private readonly apiUrl = '/api/v1';
  private authToken = '';

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<AuthResponse> {
    const cleanedUsername = (username ?? '').trim();
    const authHeader = this.buildBasicAuthHeader(cleanedUsername, password ?? '');

    return this.http.get(`${this.apiUrl}/transactions`, {
      headers: new HttpHeaders({ Authorization: authHeader }),
    }).pipe(
      map(() => ({ token: authHeader, username: cleanedUsername })),
    );
  }

  setToken(token: string): void {
    this.authToken = token ?? '';
  }

  clearToken(): void {
    this.authToken = '';
  }

  private buildBasicAuthHeader(username: string, password: string): string {
    const encoded = btoa(`${username}:${password}`);
    return `Basic ${encoded}`;
  }

  private getHeaders(): HttpHeaders {
    if (!this.authToken) {
      return new HttpHeaders();
    }

    return new HttpHeaders({
      Authorization: this.authToken,
    });
  }

  getTransactions(): Observable<Transaction[]> {
    return this.http.get<Transaction[]>(`${this.apiUrl}/transactions`, {
      headers: this.getHeaders(),
    });
  }

  createDispute(request: DisputeCreateRequest): Observable<Dispute> {
    return this.http.post<Dispute>(`${this.apiUrl}/disputes`, request, {
      headers: this.getHeaders(),
    });
  }

  getDisputes(): Observable<Dispute[]> {
    return this.http.get<Dispute[]>(`${this.apiUrl}/disputes`, {
      headers: this.getHeaders(),
    });
  }
}
