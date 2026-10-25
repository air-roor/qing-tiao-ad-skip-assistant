import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, copyFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join,resolve,dirname,basename } from 'node:path';
import { createConsole, NOTICE } from '../server.mjs';
import { hashPassword, verifyPassword, csvCell } from '../security.mjs';
const ORIGIN='http://127.0.0.1:3887';
const password='test-only-secret-choose-a-real-one';
const adminHash=await hashPassword(password);
const releaseUrl='https://github.com/air-roor/qing-tiao-ad-skip-assistant/releases/download/v0.3.0-beta.6/qing-tiao-0.3.0-beta.6.apk';
const upstream=async()=>new Response(JSON.stringify([{tag_name:'v1',assets:[{id:1,name:'test.apk',download_count:7,size:1024,browser_download_url:releaseUrl},{id:2,name:'source.zip',download_count:900,size:1}]}]),{status:200});
async function fixture(t,opts={}){
 const app=createConsole({origin:ORIGIN,adminHash,dbPath:':memory:',fetchImpl:upstream,...opts});await new Promise(resolve=>app.server.listen(0,'127.0.0.1',resolve));
 const base='http://127.0.0.1:'+app.server.address().port;
 t.after(()=>app.close());
 let cookie='';
 async function request(path,method='GET',body,headers={}){
  return fetch(base+path,{method,headers:{Origin:ORIGIN,...(cookie?{Cookie:cookie}:{}),...(body?{'Content-Type':'application/json'}:{}),...headers},body:body?JSON.stringify(body):undefined,redirect:'manual'});
 }
 async function login(){const response=await request('/api/admin/login','POST',{password});assert.equal(response.status,200);cookie=response.headers.get('set-cookie').split(';')[0];return response;}
 async function overview(){const response=await request('/api/admin/overview');assert.equal(response.status,200);return response.json();}
 return {...app,request,login,overview,base};
}
const report={category:'广告没有跳过',app:'测试应用',version:'test-only',detail:'这是隔离测试，不是用户真实反馈。',contact:'',consent:true};
function removeTestDir(path){const target=resolve(path);assert.equal(dirname(target),resolve(tmpdir()));assert.match(basename(target),/^qt-(console|restore)-/);rmSync(target,{recursive:true,force:true});}
test('password hashes are salted, checked, and do not contain plaintext',async()=>{const other=await hashPassword(password);assert.notEqual(other,adminHash);assert.equal(adminHash.includes(password),false);assert.equal(await verifyPassword(password,adminHash),true);assert.equal(await verifyPassword('wrong',adminHash),false);});
test('admin reads require authentication; CSRF and logout are enforced',async t=>{
 const f=await fixture(t);assert.equal((await f.request('/api/admin/overview')).status,401);
 assert.equal((await f.request('/api/admin/login','POST',{password},{Origin:'https://attacker.example'})).status,403);
 assert.equal((await f.request('/api/admin/login','POST',{password:'wrong'})).status,401);
 const response=await f.login();assert.match(response.headers.get('set-cookie'),/HttpOnly; SameSite=Strict/);
 assert.equal((await f.request('/api/admin/policy','PUT',{},{Origin:'https://attacker.example'})).status,403);
 assert.equal((await f.request('/api/admin/logout','POST',{})).status,200);assert.equal((await f.request('/api/admin/feedback')).status,401);
});
test('download channel tracking excludes HEAD and prefetch; does not fake installs',async t=>{
 const f=await fixture(t);const res=await f.request('/download?channel=xhs');assert.equal(res.status,302);assert.equal(res.headers.get('location'),releaseUrl);
 await f.request('/download?channel=xhs');await f.request('/download?channel=bad%20channel');await f.request('/download?channel=head','HEAD');await f.request('/download?channel=prefetch','GET',undefined,{'Sec-Purpose':'prefetch'});
 await f.login();const data=await f.overview();assert.equal(data.totals.redirects,3);assert.equal(data.totals.today,3);assert.equal(data.channels.find(c=>c.channel==='xhs').count,2);assert.equal(data.recent[0].version,'v0.3.0-beta.6');assert.equal(data.installs,undefined);assert.equal(data.github.total,7);
});
test('GitHub caches counters and preserves last-known values on upstream failure',async t=>{
 let now=Date.parse('2026-10-25T04:00:00Z'),calls=0,failed=false;
 const f=await fixture(t,{clock:()=>now,fetchImpl:async()=>{calls++;if(failed)return new Response('',{status:503});return upstream();}});
 await f.login();assert.equal((await f.overview()).github.total,7);await f.overview();assert.equal(calls,1);
 now+=121000;failed=true;let value=await f.overview();assert.equal(value.github.total,7);assert.match(value.github.error,/503/);await f.overview();assert.equal(calls,2);
});
test('feedback requires consent, strips unapproved fields and diagnostic opt-in',async t=>{
 const f=await fixture(t);assert.equal((await f.request('/api/public/feedback','POST',{...report,consent:false})).status,400);
 const response=await f.request('/api/public/feedback','POST',{...report,imei:'DO-NOT-STORE',diagnostic:'do not include',includeDiagnostic:false});assert.equal(response.status,201);const receipt=await response.json();
 assert.ok(receipt.deleteToken.length>=32);await f.login();const listing=await (await f.request('/api/admin/feedback')).json();assert.equal(listing.items.length,1);assert.equal(listing.items[0].diagnostic,'');assert.equal(JSON.stringify(listing).includes('DO-NOT-STORE'),false);assert.equal(JSON.stringify(listing).includes(receipt.deleteToken),false);assert.equal(listing.items[0].delete_hash,undefined);
 assert.equal((await f.request('/api/public/feedback/delete','POST',{id:receipt.id,deleteToken:'wrong'})).status,404);
 assert.equal((await f.request('/api/public/feedback/delete','POST',{id:receipt.id,deleteToken:receipt.deleteToken})).status,200);assert.equal((await f.overview()).totals.feedback,0);
});
test('feedback workflow persists statuses, filters, notes and safe CSV export',async t=>{
 const f=await fixture(t);const receipt=await (await f.request('/api/public/feedback','POST',{...report,app:'=HYPERLINK("x")',includeDiagnostic:true,diagnostic:'synthetic diagnostic'})).json();await f.login();
 assert.equal((await f.request('/api/admin/feedback/'+receipt.id,'PATCH',{status:'fixed',note:'resolved in test'})).status,200);
 const listing=await (await f.request('/api/admin/feedback?status=fixed')).json();assert.equal(listing.items[0].note,'resolved in test');assert.equal(listing.items[0].diagnostic,'synthetic diagnostic');assert.equal((await (await f.request('/api/admin/feedback?status=new')).json()).items.length,0);
 const csv=await (await f.request('/api/admin/export.csv')).text();assert.ok(csv.includes("'=HYPERLINK"));assert.equal(csv.includes(receipt.deleteToken),false);
});
test('fees apply to all users but live payment cannot be enabled without implementation',async t=>{
 const f=await fixture(t);let p=await (await f.request('/api/public/config')).json();assert.equal(p.allUsersRequirePayment,true);assert.equal(p.paymentEnabled,false);assert.equal(p.legacyOfflineBuildsRevocable,false);assert.match(p.notice,/公测不赠送永久免费权益/);await f.login();
 assert.equal((await f.request('/api/admin/policy','PUT',{phase:'paid',downloadUrl:releaseUrl})).status,409);
 assert.equal((await f.request('/api/admin/policy','PUT',{phase:'beta',downloadUrl:'https://attacker.example/file.apk'})).status,400);
 assert.equal((await f.request('/api/admin/policy','PUT',{phase:'announced',paymentDate:'2026-12-01',announcement:'提前公告，测试使用',downloadUrl:releaseUrl})).status,200);
 p=await (await f.request('/api/public/config')).json();assert.equal(p.phase,'announced');assert.equal(p.plannedPriceFen,990);assert.equal(p.notice,NOTICE);
});
test('feedback and login throttles prevent unbounded submissions',async t=>{
 const f=await fixture(t);for(let i=0;i<6;i++)assert.equal((await f.request('/api/public/feedback','POST',report)).status,201);
 assert.equal((await f.request('/api/public/feedback','POST',report)).status,429);
 for(let i=0;i<10;i++)assert.equal((await f.request('/api/admin/login','POST',{password:'wrong'})).status,401);
 assert.equal((await f.request('/api/admin/login','POST',{password})).status,429);
});
test('data survives restart; sessions do not; retention expires feedback',async t=>{
 const dir=mkdtempSync(join(tmpdir(),'qt-console-'));const path=join(dir,'test.sqlite');let now=Date.parse('2026-10-25T04:00:00Z');
 const first=await fixture(t,{dbPath:path,clock:()=>now});await first.request('/api/public/feedback','POST',report);await first.request('/download');await first.close();
 const second=await fixture(t,{dbPath:path,clock:()=>now});await second.login();assert.equal((await second.overview()).totals.feedback,1);assert.equal((await second.overview()).totals.redirects,1);
 now+=91*86400000;await second.login();const data=await second.overview();assert.equal(data.totals.feedback,0);assert.equal(data.totals.redirects,1);
 await second.close();removeTestDir(dir);
});
test('Shanghai day boundary, public hardening and non-HTML feedback are preserved',async t=>{
 let now=Date.parse('2026-10-25T15:59:00Z');const f=await fixture(t,{clock:()=>now});await f.request('/download');now+=120000;await f.login();assert.equal((await f.overview()).totals.today,0);
 const page=await f.request('/admin');assert.match(page.headers.get('content-security-policy'),/frame-ancestors 'none'/);assert.equal(page.headers.get('cache-control'),'no-store');
 const malicious='<img src=x onerror=alert(1)>';await f.request('/api/public/feedback','POST',{...report,detail:malicious});const data=await (await f.request('/api/admin/feedback')).json();assert.equal(data.items[0].detail,malicious);
 assert.equal((await f.request('/api/public/feedback','POST',report,{Origin:'https://other.example'})).status,403);
});
test('CSV export neutralizes formulas after whitespace',()=>{for(const value of ['=1+1','  +cmd','\t@cmd','-1'])assert.match(csvCell(value),/^"'/);assert.equal(csvCell('plain "quote"'),'"plain ""quote"""');});
test('backup restore replays deletion ledger without reviving deleted feedback',async t=>{
 const dir=mkdtempSync(join(tmpdir(),'qt-restore-')),path=join(dir,'console.sqlite'),snapshot=join(dir,'backup.sqlite');
 const first=await fixture(t,{dbPath:path});const receipt=await(await first.request('/api/public/feedback','POST',report)).json();await first.snapshot(snapshot);
 assert.equal((await first.request('/api/public/feedback/delete','POST',{id:receipt.id,deleteToken:receipt.deleteToken})).status,200);await first.close();copyFileSync(snapshot,path);
 const second=await fixture(t,{dbPath:path});await second.login();assert.equal((await second.overview()).totals.feedback,0);await second.close();removeTestDir(dir);
});
