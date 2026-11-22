import {test,expect} from '@playwright/test'

/** No live backend, credentials, DB or external service is involved. */
test('synthetic two-workspace Persian RTL UI and loopback-only HTTP mock',async({page})=>{
  const touched=[]
  await page.route('**/*',route=>{
    const u=new URL(route.request().url())
    touched.push(u.host)
    if(!['127.0.0.1','localhost'].includes(u.hostname))return route.abort('blockedbyclient')
    if(u.pathname==='/__phase06__/workspaces')return route.fulfill({status:200,contentType:'application/json',
      headers:{'access-control-allow-origin':'*'},body:JSON.stringify({scope:'TEST',data:[{id:'synthetic-workspace-a'},{id:'synthetic-workspace-b'}]})})
    return route.abort('blockedbyclient')
  })
  await page.setContent(`<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8">
    <main><h1>سامانه ساخت‌یار - محیط آزمایشی</h1><label for="w">فضای کار</label>
    <select id="w" aria-label="فضای کار"><option value="synthetic-workspace-a">آزمایشی الف</option>
    <option value="synthetic-workspace-b">آزمایشی ب</option></select></main></html>`)
  await expect(page.locator('html')).toHaveAttribute('dir','rtl')
  await expect(page.getByRole('heading',{name:/ساخت‌یار/})).toBeVisible()
  await page.getByLabel('فضای کار').selectOption('synthetic-workspace-b')
  await expect(page.getByLabel('فضای کار')).toHaveValue('synthetic-workspace-b')
  const data=await page.evaluate(async()=>await(await fetch('http://127.0.0.1:5175/__phase06__/workspaces')).json())
  expect(data.scope).toBe('TEST')
  expect(data.data).toHaveLength(2)
  await expect.poll(async()=>page.evaluate(async()=>{
    try{await fetch('https://example.org/restricted');return 'unexpected'}catch{return 'denied'}
  })).toBe('denied')
  expect(touched.some(host=>host==='example.org')).toBe(true)
  // Never enable browser screenshots, traces, videos, HAR or browser context storage uploads in CI.
})
