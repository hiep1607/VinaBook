import { Component, OnDestroy, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';

import { ThemeService } from './services/theme.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})

export class AppComponent implements OnInit, OnDestroy {

  title = 'bookstore-ui';
  private routeSub?: Subscription;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private titleService: Title,
    private themeService: ThemeService
  ) {
    this.themeService.init();
  }

  ngOnInit(): void {
    this.routeSub = this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      map(() => {
        let current = this.route.firstChild;
        while (current && current.firstChild) {
          current = current.firstChild;
        }
        return current && current.snapshot.data['pageTitle']
          ? current.snapshot.data['pageTitle']
          : 'VinaBook';
      })
    ).subscribe(pageTitle => this.titleService.setTitle(`${pageTitle} | VinaBook`));
  }

  ngOnDestroy(): void {
    if (this.routeSub) {
      this.routeSub.unsubscribe();
    }
  }

}
