import { Component, OnInit } from '@angular/core';
import { Chart } from 'chart.js';

@Component({
  selector: 'app-admin',
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.css']
})
export class AdminComponent implements OnInit {

  constructor() { }

  ngOnInit(): void {
    // Canvas labels do not inherit CSS typography; use the admin body font.
    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
  }

}
