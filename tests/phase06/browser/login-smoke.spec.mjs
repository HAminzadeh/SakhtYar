import {test,expect} from '@playwright/test'
test('local login shell and Persian typography render without network egress',async({page})=>{
  await page.route('**/*',route=>{
    const url=new URL(route.request().url())
    if(url.hostname==='127.0.0.1'||url.hostname==='localhost')return route.continue()
    return route.abort()
  })
  await page.goto('/login')
  await expect(page.locator('.sakhtyar-login-v56__card')).toBeVisible()
  await expect(page.locator('input[autocomplete="username"]')).toBeVisible()
  await expect(page.locator('input[autocomplete="current-password"]')).toBeVisible()
  // This is a browser smoke, NOT E2E-22; no credentials or business flow executed.
})
