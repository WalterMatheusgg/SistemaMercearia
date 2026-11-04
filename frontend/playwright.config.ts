import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir:'./e2e',fullyParallel:false,workers:1,retries:process.env['CI']?1:0,timeout:90000,
  use:{baseURL:process.env['E2E_BASE_URL']??'http://localhost:8080',trace:'retain-on-failure',screenshot:'only-on-failure'},
  reporter:[['list'],['html',{open:'never'}]],
  projects:[{name:'desktop',use:{...devices['Desktop Chrome']}},{name:'mobile',use:{...devices['Pixel 7']}}],
});
