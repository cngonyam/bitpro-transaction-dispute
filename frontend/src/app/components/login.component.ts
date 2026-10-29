import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PortalStore } from '../portal.store';

interface DemoUserOption {
  label: string;
  username: string;
  password: string;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="app-shell login-screen">
      <div class="panel login-panel">
        <div class="brand-block login-brand">
          <div class="brand-mark">B</div>
          <div>
            <p class="eyebrow">Bitpro Holdings</p>
            <h1>Dispute Operations Portal</h1>
          </div>
        </div>

        <div class="login-header">
          <h2>Secure sign in</h2>
          <p>Use your Bitpro credentials to access the dispute dashboard.</p>
        </div>

        @if (store.errorMessage()) {
          <div class="notice error">{{ store.errorMessage() }}</div>
        }

        <form class="login-form" (ngSubmit)="login()">
          <label>
            Username
            <input type="text" [(ngModel)]="username" name="loginUsername" placeholder="Enter username" required />
          </label>

          <label>
            Password
            <input type="password" [(ngModel)]="password" name="loginPassword" placeholder="Enter password" required />
          </label>

          <div class="credential-hint">
            <span>Demo accounts</span>
            <strong>admin-user / admin-pass123 · agent-user / agent-pass123 · lead-user / lead-pass123 · customer-user / customer-pass123</strong>
          </div>

          <div class="demo-user-picker" aria-label="Demo users">
            @for (user of demoUsers; track user.username) {
              <button type="button" class="secondary-button" (click)="selectDemoUser(user)">{{ user.label }}</button>
            }
          </div>

          <button type="submit" class="primary-button" [disabled]="store.isLoggingIn()">
            {{ store.isLoggingIn() ? 'Signing in...' : 'Sign in' }}
          </button>
        </form>
      </div>
    </div>
  `,
})
export class LoginComponent {
  protected readonly store = inject(PortalStore);

  protected username = 'lead-user';
  protected password = 'lead-pass123';

  protected readonly demoUsers: DemoUserOption[] = [
    { label: 'Admin', username: 'admin-user', password: 'admin-pass123' },
    { label: 'Agent', username: 'agent-user', password: 'agent-pass123' },
    { label: 'Lead', username: 'lead-user', password: 'lead-pass123' },
    { label: 'Customer', username: 'customer-user', password: 'customer-pass123' },
  ];

  protected selectDemoUser(option: DemoUserOption): void {
    this.username = option.username;
    this.password = option.password;
    this.store.errorMessage.set('');
  }

  protected login(): void {
    this.store.login(this.username, this.password);
  }
}
