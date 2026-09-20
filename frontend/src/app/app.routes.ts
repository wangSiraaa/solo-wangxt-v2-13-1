import { Routes } from '@angular/router';
import { BilingualReviewComponent } from './review/bilingual-review.component';
import { VersionLineageComponent } from './lineage/version-lineage.component';

export const routes: Routes = [
  { path: '', redirectTo: 'review', pathMatch: 'full' },
  { path: 'review', component: BilingualReviewComponent },
  { path: 'versions', component: VersionLineageComponent },
];
