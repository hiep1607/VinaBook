import { Injectable } from '@angular/core';

export interface Theme {
  id: string;
  name: string;
  primary: string;
  accent: string;
  bg: string;
}

@Injectable({ providedIn: 'root' })
export class ThemeService {

  private readonly storageKey = 'theme';
  private readonly defaultTheme = 'editorial';

  readonly themes: Theme[] = [
    { id: 'editorial', name: 'VinaBook hiện đại', primary: '#123f32', accent: '#b9471d', bg: '#f6f8f7' },
    { id: 'ocean', name: 'Đại dương', primary: '#155e75', accent: '#0e7490', bg: '#f8fafc' },
    { id: 'forest', name: 'Rừng xanh', primary: '#166534', accent: '#a16207', bg: '#f7f8f5' },
    { id: 'berry', name: 'Mận chín', primary: '#831843', accent: '#e11d48', bg: '#fdfafb' },
    { id: 'dark', name: 'Ban đêm', primary: '#168563', accent: '#f97316', bg: '#101613' }
  ];

  current = this.defaultTheme;

  init(): void {
    const saved = localStorage.getItem(this.storageKey);
    const valid = saved && this.themes.some(theme => theme.id === saved);
    this.setTheme(valid ? saved as string : this.defaultTheme);
  }

  setTheme(id: string): void {
    if (!this.themes.some(theme => theme.id === id)) {
      return;
    }

    this.current = id;
    document.documentElement.setAttribute('data-theme', id);
    localStorage.setItem(this.storageKey, id);
  }
}
