import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

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

@Injectable({ providedIn: 'root' })
export class TransactionDisputeService {
  private readonly apiUrl = 'http://localhost:8082/api/v1';

  constructor(private http: HttpClient) {}

  getTransactions(): Observable<Transaction[]> {
    return this.http.get<Transaction[]>(`${this.apiUrl}/transactions`);
  }

  createDispute(request: DisputeCreateRequest): Observable<Dispute> {
    return this.http.post<Dispute>(`${this.apiUrl}/disputes`, request);
  }

  getDisputes(): Observable<Dispute[]> {
    return this.http.get<Dispute[]>(`${this.apiUrl}/disputes`);
  }
}
