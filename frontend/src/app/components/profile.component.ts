import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PortalStore } from '../portal.store';
import { UserProfile } from '../transaction-dispute.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="panel">
      <div class="panel-header">
        <h2>Manage profile</h2>
        <span>Operations profile</span>
      </div>

      <form class="profile-form" (ngSubmit)="save()">
        <div class="field-grid">
          <label>
            Full name
            <input type="text" [(ngModel)]="draft.fullName" name="fullName" />
          </label>

          <label>
            Role
            <input type="text" [(ngModel)]="draft.role" name="role" />
          </label>

          <label>
            Email
            <input type="email" [(ngModel)]="draft.email" name="email" />
          </label>

          <label>
            Phone
            <input type="tel" [(ngModel)]="draft.phone" name="phone" />
          </label>

          <label>
            Department
            <input type="text" [(ngModel)]="draft.department" name="department" />
          </label>

          <label>
            Region
            <input type="text" [(ngModel)]="draft.region" name="region" />
          </label>
        </div>

        <button type="submit" class="primary-button">Save profile</button>
      </form>
    </section>
  `,
})
export class ProfileComponent {
  private readonly store = inject(PortalStore);

  protected draft: UserProfile = { ...this.store.profile() };

  protected save(): void {
    this.store.updateProfile(this.draft);
  }
}
