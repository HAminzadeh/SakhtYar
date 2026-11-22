import { test, expect } from '@playwright/test'

// E2E-22 synthetic browser only. No application authentication, fixture, or DB.
// This cannot constitute business acceptance. Never record screenshot/video/trace.
for (const viewport of [{width:1280,height:800},{width:390,height:844}]) {
  test(`phase3 synthetic RTL and deny egress ${viewport.width}`, async ({page,context}) => {
    await page.setViewportSize(viewport)
    const requests=[]
    await context.route('**/*', async route => {
      const url=new URL(route.request().url())
      requests.push(url.origin)
      if (url.origin !== 'http://127.0.0.1:5175') return route.abort('blockedbyclient')
      return route.fulfill({status:200,contentType:'application/json',body:'{"mode":"SYNTHETIC"}'})
    })
    await page.setContent('<html lang="fa" dir="rtl"><head><meta charset="utf-8"></head><body><main><h1>شبیه‌سازی آفلاین</h1><button id="safe">بررسی</button><output id="status">آماده</output></main></body></html>')
    await expect(page.locator('html')).toHaveAttribute('dir','rtl')
    await expect(page.getByRole('heading',{name:'شبیه‌سازی آفلاین'})).toBeVisible()
    await expect(page.locator('#safe')).toBeVisible()
    await page.locator('#safe').click()
    await page.evaluate(() => { document.querySelector('#status').textContent='موفق' })
    await expect(page.locator('#status')).toHaveText('موفق')
    expect(requests.every(x=>x==='http://127.0.0.1:5175')).toBe(true)
  })
}
