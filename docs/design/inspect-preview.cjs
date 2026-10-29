// Visual review helper for T02; not an application test suite.
// BL_PLAYWRIGHT_MODULE: local Playwright module path, if not installed in this repo.
// BL_CHROME: optional installed Chrome executable path.
// Run against the visualize render.py --serve preview at BL_PREVIEW_URL.
const { chromium } = require(process.env.BL_PLAYWRIGHT_MODULE || 'playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

(async () => {
  const out = path.join(__dirname, 'qa');
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ headless: true, ...(process.env.BL_CHROME ? {executablePath:process.env.BL_CHROME} : {}) });
  // The standalone wrapper contributes 16px padding on either side.
  const page = await browser.newPage({viewport:{width:1472,height:1150},colorScheme:'light'});
  const errors = [];
  const evidence = [];
  page.on('pageerror',e=>errors.push(e.message));
  // The mockup uses only labeled controls; external host enhancement scripts are
  // disabled so layout and interactions can be checked without network access.
  await page.route('https://**/*',r=>r.fulfill({status:200,contentType:'application/javascript',body:''}));
  await page.addInitScript(() => {
    globalThis.__designControls=[];
    globalThis.Tweak=class {
      constructor(options){this.options=options;}
      bind(obj,key,config){globalThis.__designControls.push({obj,key,config,change:this.options.onChange});}
      addSelect(...args){this.bind(...args);}
      addToggle(...args){this.bind(...args);}
      addSlider(...args){this.bind(...args);}
      addColorPicker(...args){this.bind(...args);}
    };
  });
  await page.goto(process.env.BL_PREVIEW_URL || 'http://127.0.0.1:8768/',{waitUntil:'load'});
  const f = page.frameLocator('iframe');
  await f.locator('#backend-lab-design').waitFor();
  const frame = page.frames().find(x=>x.parentFrame());
  assert(frame,'Preview must be in the sandboxed iframe');
  const set = (key,value) => frame.evaluate(({key,value}) => {
    const c=globalThis.__designControls.find(c=>c.key===key);
    if(!c)throw Error('Missing design control '+key);
    c.obj[key]=value;c.change();
  },{key,value});
  const shot = async name => {
    const height=await f.locator('#backend-lab-design').evaluate(e=>Math.ceil(e.getBoundingClientRect().height));
    await page.setViewportSize({width:page.viewportSize().width,height:Math.max(1150,height+64)});
    await page.locator('iframe').evaluate((el,height)=>{el.style.height=(height+8)+'px';},height);
    await frame.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
    await f.locator('#backend-lab-design').screenshot({path:path.join(out,name+'.png')});
    evidence.push(name);
  };
  const noOverflow = async label => {
    const bad = await frame.evaluate(() => {
      const root=document.getElementById('backend-lab-design');
      const bounds=root.getBoundingClientRect();
      return [...root.querySelectorAll('*')].filter(e=>{
        if(!e.getClientRects().length||getComputedStyle(e).position==='absolute')return false;
        const r=e.getBoundingClientRect();
        return r.right>bounds.right+2 || r.left<bounds.left-2;
      }).slice(0,5).map(e=>e.tagName+'.'+e.className);
    });
    assert.deepEqual(bad,[],label+' horizontal overflow');
  };

  await shot('01-home-desktop');
  await f.locator('[data-action="start"]').click();
  await f.locator('[data-action="run"]').click();
  assert.match(await f.locator('.bl-stage').innerText(),/500 · Lỗi tại service/);
  await shot('02-lab-error-desktop');
  await f.locator('[data-tab="code"]').click();
  await f.locator('[data-action="expand"]').click();
  assert(await f.locator('.bl-stage').isHidden());
  await shot('03-code-expanded-desktop');
  await f.locator('[data-action="expand"]').click();
  await f.locator('[data-action="repair"]').click();
  await f.locator('[data-action="run"]').click();
  assert.match(await f.locator('.bl-stage').innerText(),/400 · Input bị từ chối/);
  await shot('04-lab-repaired-desktop');
  await f.locator('[data-control="preset"]').selectOption('valid');
  await f.locator('[data-action="run"]').click();
  assert.match(await f.locator('.bl-stage').innerText(),/201 · Đã tạo task/);
  await f.locator('[data-action="challenge"]').click();
  await f.locator('input[value="null"]').check();
  await f.locator('[data-action="check"]').click();
  assert.match(await f.locator('[role="status"]').innerText(),/null chưa đủ/);
  await shot('05-challenge-fail-desktop');
  await f.locator('input[value="blank"]').check();
  await f.locator('[data-action="check"]').click();
  assert.match(await f.locator('[role="status"]').innerText(),/giữ đúng loại lỗi/);
  await f.locator('[data-action="result"]').click();
  await set('saveError',true);
  await shot('06-result-save-error-desktop');
  await f.locator('[data-action="retry-save"]').click();
  assert.equal(await f.locator('[role="alert"]').count(),0);
  await set('screen','journey');
  await shot('07-journey-desktop');
  await f.locator('[data-track="devops"]').click();
  assert.equal(await f.locator('[data-action="start"]').count(),0);
  await f.locator('[data-track="java"]').click();
  await set('screen','home');
  await set('experience','resume');
  await set('wireframe',true);
  await shot('08-home-wireframe');
  await set('wireframe',false);

  let layoutCases=0;
  for(const width of [1440,1280,1024,736,390,320]){
    await page.setViewportSize({width:width+32,height:1150});
    assert.equal(Math.round((await f.locator('#backend-lab-design').boundingBox()).width),width);
    for(const screen of ['home','journey','lab','challenge','result']){
      await set('screen',screen);await noOverflow(width+'/'+screen);layoutCases++;
    }
    if(width===1280){await set('screen','lab');await shot('09-lab-laptop');}
    if(width===390){
      await set('screen','home');await shot('10-home-mobile');
      await set('screen','lab');await shot('11-lab-mobile');
      await set('screen','result');await shot('12-result-mobile');
    }
  }
  await page.setViewportSize({width:1472,height:1150});
  await set('screen','lab');await set('lab','failed');
  assert.match(await f.locator('.bl-stage .bl-inline-note').innerText(),/title = null/);
  await set('appearance','dark');await shot('13-lab-dark');await noOverflow('dark');
  await set('accent','blue');await noOverflow('blue');
  await set('mobile',true);await noOverflow('forced-mobile');
  await page.emulateMedia({reducedMotion:'reduce'});
  await set('screen','home');
  await f.locator('[data-action="start"]').focus();
  await page.keyboard.press('Enter');
  assert.match(await f.locator('h1').innerText(),/Cứu API/);
  assert.deepEqual(errors,[],'Browser runtime errors');
  const report={date:new Date().toISOString(),browser:await browser.version(),layoutCases,screenshots:evidence,runtimeErrors:errors,checks:['navigation','500 trace','expanded code','400 validation','201 valid title','wrong/right feedback','save error state','unpublished DevOps','six responsive widths','dark theme','blue variant','forced mobile','keyboard Enter with reduced motion'],limitations:['T02 UI mockup only; no production grading or persistent learning data.','External host scripts blocked in QA; optional Lucide icons absent in captures.','No learner usability session or assistive-technology audit performed.']};
  fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify(report,null,2));
  await browser.close();
})().catch(e=>{console.error(e);process.exitCode=1;});
