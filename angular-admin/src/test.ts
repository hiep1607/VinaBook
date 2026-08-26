// This file is required by karma.conf.js and loads recursively all the .spec and framework files

import 'zone.js/testing';
import { CommonModule } from '@angular/common';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { getTestBed, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { MatTableExporterModule } from 'mat-table-exporter';
import { OrderModule } from 'ngx-order-pipe';
import { ToastrModule } from 'ngx-toastr';
import {
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting
} from '@angular/platform-browser-dynamic/testing';

declare const require: {
  context(path: string, deep?: boolean, filter?: RegExp): {
    keys(): string[];
    <T>(id: string): T;
  };
};

// First, initialize the Angular testing environment.
getTestBed().initTestEnvironment(
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting()
);

// Keep the legacy generated smoke specs useful by providing their shared
// Angular infrastructure in one place.
const configureTestingModule = TestBed.configureTestingModule.bind(TestBed);
(TestBed as any).configureTestingModule = (metadata: any = {}) => configureTestingModule({
  ...metadata,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    HttpClientTestingModule,
    RouterTestingModule,
    NoopAnimationsModule,
    NgbModule,
    MatTableExporterModule,
    OrderModule,
    ToastrModule.forRoot(),
    ...(metadata.imports || [])
  ],
  schemas: [NO_ERRORS_SCHEMA, ...(metadata.schemas || [])]
});
// Then we find all the tests.
const context = require.context('./', true, /\.spec\.ts$/);
// And load the modules.
context.keys().map(context);
