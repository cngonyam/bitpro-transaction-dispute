import { Component, ViewEncapsulation, inject } from '@angular/core';
import { DisputeHistoryComponent } from './components/dispute-history.component';
import { LoginComponent } from './components/login.component';
import { ProfileComponent } from './components/profile.component';
import { SubmitDisputeComponent } from './components/submit-dispute.component';
import { TrackDisputeComponent } from './components/track-dispute.component';
import { TransactionsComponent } from './components/transactions.component';
import { PortalStore } from './portal.store';
import { DashboardTab } from './portal.utils';

export type { DashboardTab } from './portal.utils';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    LoginComponent,
    TransactionsComponent,
    SubmitDisputeComponent,
    TrackDisputeComponent,
    DisputeHistoryComponent,
    ProfileComponent,
  ],
  templateUrl: './app.html',
  styleUrl: './app.css',
  // The stylesheet is shared by every portal component, so it is applied globally.
  encapsulation: ViewEncapsulation.None,
})
export class App {
  protected readonly store = inject(PortalStore);

  protected readonly tabs: { id: DashboardTab; label: string }[] = [
    { id: 'transactions', label: 'View transactions' },
    { id: 'new-dispute', label: 'Submit dispute' },
    { id: 'track-dispute', label: 'Track dispute status' },
    { id: 'dispute-history', label: 'View dispute history' },
    { id: 'profile', label: 'Manage profile' },
  ];
}
