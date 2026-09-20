import { Routes } from '@angular/router';
import { BatchesComponent } from './pages/batches.component';
import { BatchDetailComponent } from './pages/batch-detail.component';
import { TasksComponent } from './pages/tasks.component';
import { VersionsComponent } from './pages/versions.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'batches' },
  { path: 'batches', component: BatchesComponent },
  { path: 'batches/:id', component: BatchDetailComponent },
  { path: 'tasks', component: TasksComponent },
  { path: 'versions', component: VersionsComponent },
];
