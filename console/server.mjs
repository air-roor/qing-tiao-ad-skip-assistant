import { createServer } from 'node:http';
import { DatabaseSync, backup } from 'node:sqlite';
import { readFileSync, mkdirSync, existsSync, appendFileSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join, resolve } from 'node:path';
import { isIP } from 'node:net';
import { randomUUID } from 'node:crypto';
import { secret, digest, verifyPassword, csvCell } from './security.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = 'air-roor/qing-tiao-ad-skip-assistant';
const DEFAULT_URL = `https://github.com/${REPO}/releases/download/v0.3.0-beta.7/qing-tiao-0.3.0-beta.7.apk`;
export const NOTICE = '当前为免费公测。正式收费后，所有用户（包括参加过免费公测的用户）均需登录账号并购买使用权益，公测不赠送永久免费权益。收费日期与最终价格将提前公告。';
const CATEGORIES = ['广告没有跳过','跳过速度慢','误点了普通按钮','无障碍自动关闭','应用选择问题','其他问题'];
const STATUSES = ['new','investigating','fixed','closed'];
const clean = (value, max) => String(value ?? '').normalize('NFKC').replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g,'').trim().slice(0,max);
class HttpError extends Error { constructor(status,message){super(message);this.status=status;} }
const fail = (status,message) => {throw new HttpError(status,message);};
export function createConsole(options={}) {
  const clock = options.clock || Date.now;
  const origin = options.origin || process.env.PUBLIC_ORIGIN || 'http://127.0.0.1:3887';
  const parsedOrigin = new URL(origin);
  if (parsedOrigin.origin !== origin || !['http:','https:'].includes(parsedOrigin.protocol)) throw new Error('PUBLIC_ORIGIN must be an exact http(s) origin, without a trailing slash');
  if(parsedOrigin.protocol !== 'https:' && !['127.0.0.1','localhost','[::1]'].includes(parsedOrigin.hostname)) throw new Error('Public deployment requires HTTPS');
  const adminHash = options.adminHash || process.env.ADMIN_PASSWORD_HASH;
  if(!adminHash?.startsWith('scrypt:'))throw new Error('Run node setup.mjs first; ADMIN_PASSWORD_HASH is required');
  const dbPath=options.dbPath || resolve(HERE,process.env.DATA_DIR || 'data','console.sqlite');
  if(dbPath!==':memory:')mkdirSync(dirname(dbPath),{recursive:true,mode:0o700});
  const db=new DatabaseSync(dbPath);
  db.exec(`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
    CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS downloads (id INTEGER PRIMARY KEY, at INTEGER NOT NULL, channel TEXT NOT NULL, version TEXT NOT NULL);
    CREATE INDEX IF NOT EXISTS downloads_at ON downloads(at);
    CREATE TABLE IF NOT EXISTS feedback (id TEXT PRIMARY KEY, at INTEGER NOT NULL, category TEXT NOT NULL, app TEXT NOT NULL, version TEXT NOT NULL, device TEXT NOT NULL, system TEXT NOT NULL, detail TEXT NOT NULL, contact TEXT NOT NULL, diagnostic TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'new', note TEXT NOT NULL DEFAULT '', delete_hash TEXT NOT NULL);
    CREATE INDEX IF NOT EXISTS feedback_at ON feedback(at);
    CREATE TABLE IF NOT EXISTS audit (id INTEGER PRIMARY KEY, at INTEGER NOT NULL, action TEXT NOT NULL, target TEXT NOT NULL);
  `);
  if(!db.prepare('PRAGMA table_info(feedback)').all().some(column=>column.name==='revision'))db.exec('ALTER TABLE feedback ADD COLUMN revision INTEGER NOT NULL DEFAULT 0');
  const deletionLedger=dbPath===':memory:'?null:join(dirname(dbPath),'feedback-deletions.jsonl');
  if(deletionLedger&&existsSync(deletionLedger)){
    for(const line of readFileSync(deletionLedger,'utf8').split('\n').filter(Boolean)){
      const entry=JSON.parse(line);
      if(typeof entry.id!=='string'||!/^QJ-[a-f0-9-]{36}$/.test(entry.id))throw Error('Deletion ledger is invalid; restore stopped');
      db.prepare('DELETE FROM feedback WHERE id=?').run(entry.id);
    }
  }
  const setting=(key,fallback)=>{const row=db.prepare('SELECT value FROM settings WHERE key=?').get(key);return row?JSON.parse(row.value):fallback;};
  const set=(key,value)=>db.prepare('INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value').run(key,JSON.stringify(value));
  const policy=()=>({publicOrigin:origin,phase:setting('phase','beta'),notice:NOTICE,plannedPriceFen:990,allUsersRequirePayment:true,announcement:setting('announcement',''),paymentDate:setting('paymentDate',null),downloadUrl:setting('downloadUrl',DEFAULT_URL),paymentEnabled:false,legacyOfflineBuildsRevocable:false});
  const audit=(action,target)=>db.prepare('INSERT INTO audit(at,action,target) VALUES(?,?,?)').run(clock(),action,target);
  const version=()=>{const url=new URL(policy().downloadUrl);return decodeURIComponent(url.pathname.split('/')[5] || 'unknown');};
  const sessions=new Map(),limits=new Map();
  let loginJobs=0,lastPurge=0;
  let github=setting('github',{updatedAt:null,assets:[],total:null,error:null}),githubPending;
  function purge(){
    if(clock()-lastPurge<60_000)return;
    const now=clock();lastPurge=now;
    db.prepare('DELETE FROM feedback WHERE at < ?').run(now-90*86400_000);
    db.prepare('DELETE FROM downloads WHERE at < ?').run(now-365*86400_000);
    db.prepare('DELETE FROM audit WHERE at < ?').run(now-180*86400_000);
    for(const [key,value] of limits)if(value.until<now)limits.delete(key);
    for(const [key,value] of sessions)if(value<now)sessions.delete(key);
  }
  function remote(req){
    const ip=req.socket.remoteAddress || 'unknown';
    if(process.env.TRUST_PROXY==='1' && ['127.0.0.1','::1','::ffff:127.0.0.1'].includes(ip)){
      const candidate=String(req.headers['x-forwarded-for'] || '').split(',').at(-1).trim();
      if(isIP(candidate))return candidate;
    }
    return ip;
  }
  function limit(req,key,max,window=600_000){
    const now=clock(),id=`${key}:${digest(remote(req))}`;
    let slot=limits.get(id);if(!slot||slot.until<now){if(!slot&&limits.size>=20_000)fail(503,'服务繁忙，请稍后再试');slot={n:0,until:now+window};limits.set(id,slot);}
    if(++slot.n>max)fail(429,'操作过于频繁，请稍后再试');
  }
  function auth(req){
    const cookie=String(req.headers.cookie || '').split(';').map(v=>v.trim()).find(v=>v.startsWith('qt_admin='))?.slice(9);
    const key=cookie?digest(cookie):'';
    if(!key || (sessions.get(key)||0)<=clock())fail(401,'请先登录管理后台');
    return key;
  }
  function sameOrigin(req){if(req.headers.origin!==origin)fail(403,'请求来源不匹配，请从本站页面操作');}
  async function jsonBody(req){
    if(!String(req.headers['content-type']||'').startsWith('application/json'))fail(415,'请使用 JSON 请求');
    const chunks=[];let length=0;
    for await(const chunk of req){length+=chunk.length;if(length>32768)fail(413,'内容过长');chunks.push(chunk);}
    try {const value=JSON.parse(Buffer.concat(chunks).toString('utf8'));if(!value||typeof value!=='object'||Array.isArray(value))throw Error();return value;}
    catch {fail(400,'内容格式不正确');}
  }
  function json(res,status,value){res.writeHead(status,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(value));}
  async function githubSync(){
    if(github.updatedAt && clock()-github.updatedAt<120_000)return github;
    if(githubPending)return githubPending;
    githubPending=(async()=>{
      try{
        const assets=[];
        for(let page=1;page<=5;page++){
          const headers={Accept:'application/vnd.github+json','User-Agent':'QingTiao-Beta-Console','X-GitHub-Api-Version':'2022-11-28'};
          const token=options.githubToken ?? process.env.GITHUB_TOKEN;if(token)headers.Authorization=`Bearer ${token}`;
          const response=await (options.fetchImpl || fetch)(`https://api.github.com/repos/${REPO}/releases?per_page=100&page=${page}`,{headers,signal:AbortSignal.timeout(12_000),redirect:'error'});
          if(!response.ok)throw new Error(`GitHub HTTP ${response.status}`);
          const releases=await response.json();if(!Array.isArray(releases))throw Error('GitHub response invalid');
          for(const release of releases)if(!release.draft)for(const asset of release.assets||[])if(asset.name?.endsWith('.apk')){
            if(!Number.isSafeInteger(asset.download_count)||asset.download_count<0)throw Error('GitHub asset counter invalid');
            assets.push({id:asset.id,version:release.tag_name,name:asset.name,downloads:asset.download_count,size:asset.size,url:asset.browser_download_url});
          }
          if(releases.length<100)break;
        }
        github={updatedAt:clock(),assets,total:assets.reduce((sum,a)=>sum+a.downloads,0),error:null};set('github',github);
      }catch(error){github={...github,error:clean(error.message,120),retryAt:clock()+120_000};}
      finally{githubPending=null;}
      return github;
    })();
    return githubPending;
  }
  function feedbackList(url,exporting=false){
    const status=url.searchParams.get('status')||'',category=url.searchParams.get('category')||'',query=clean(url.searchParams.get('q'),120).toLowerCase();
    if(status&&!STATUSES.includes(status))fail(400,'筛选状态无效');if(category&&!CATEGORIES.includes(category))fail(400,'问题类型无效');
    const where=[],args=[];
    if(status){where.push('status=?');args.push(status);}if(category){where.push('category=?');args.push(category);}
    if(query){where.push('(instr(lower(id),?)>0 OR instr(lower(app),?)>0 OR instr(lower(detail),?)>0 OR instr(lower(device),?)>0 OR instr(lower(version),?)>0 OR instr(lower(note),?)>0)');args.push(...Array(6).fill(query));}
    const suffix=where.length?' WHERE '+where.join(' AND '):'';
    const total=db.prepare('SELECT COUNT(*) n FROM feedback'+suffix).get(...args).n;
    const rawPage=url.searchParams.get('page')||'1',rawSize=url.searchParams.get('pageSize')||'20';
    if(!/^\d{1,8}$/.test(rawPage)||Number(rawPage)<1||![10,20,50,100].includes(Number(rawSize)))fail(400,'分页参数无效');
    const pageSize=exporting?5000:Number(rawSize),totalPages=Math.max(1,Math.ceil(total/pageSize)),page=exporting?1:Math.min(Number(rawPage),totalPages);
    const items=db.prepare('SELECT id,at,category,app,version,device,system,detail,contact,diagnostic,status,note,revision FROM feedback'+suffix+' ORDER BY at DESC,id DESC LIMIT ? OFFSET ?').all(...args,pageSize,(page-1)*pageSize);
    return {items,total,page,pageSize,totalPages};
  }
  const server=createServer(async(req,res)=>{
    res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','same-origin');
    res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
    if(parsedOrigin.protocol==='https:')res.setHeader('Strict-Transport-Security','max-age=31536000');
    try{
      purge();const url=new URL(req.url,origin),path=url.pathname,method=req.method;
      if(method==='GET' && path==='/health')return json(res,200,{ok:true,service:'qing-tiao-console'});
      if(method==='GET' && path==='/api/public/config')return json(res,200,policy());
      if(method==='POST' && path==='/api/admin/login'){
        sameOrigin(req);limit(req,'login',10);if(loginJobs>=3)fail(429,'请稍后再试');const body=await jsonBody(req);loginJobs++;
        let valid;try{valid=await verifyPassword(clean(body.password,256),adminHash);}finally{loginJobs--;}
        if(!valid)fail(401,'密码不正确');const token=secret();sessions.set(digest(token),clock()+12*3600_000);
        res.setHeader('Set-Cookie',`qt_admin=${token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=43200${parsedOrigin.protocol==='https:'?'; Secure':''}`);return json(res,200,{ok:true});
      }
      if(path.startsWith('/api/admin/')){
        const session=auth(req);if(method!=='GET')sameOrigin(req);
        if(method==='POST' && path==='/api/admin/logout'){
          sessions.delete(session);res.setHeader('Set-Cookie','qt_admin=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0');return json(res,200,{ok:true});
        }
        if(method==='GET' && path==='/api/admin/overview'){
          if(!github.retryAt || clock()>=github.retryAt)await githubSync();
          const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(clock()));
          const since=Date.parse(`${today}T00:00:00+08:00`);
          return json(res,200,{asOf:clock(),policy:policy(),github,githubRefreshSeconds:120,
            totals:{redirects:db.prepare('SELECT COUNT(*) n FROM downloads').get().n,today:db.prepare('SELECT COUNT(*) n FROM downloads WHERE at>=?').get(since).n,feedback:db.prepare('SELECT COUNT(*) n FROM feedback').get().n,newFeedback:db.prepare("SELECT COUNT(*) n FROM feedback WHERE status='new'").get().n},
            channels:db.prepare('SELECT channel,COUNT(*) count FROM downloads GROUP BY channel ORDER BY count DESC').all(),
            trend:db.prepare("SELECT strftime('%Y-%m-%d',at/1000,'unixepoch','+8 hours') day,COUNT(*) count FROM downloads WHERE at>=? GROUP BY day ORDER BY day").all(clock()-14*86400_000),
            feedbackByStatus:db.prepare('SELECT status,COUNT(*) count FROM feedback GROUP BY status').all(),
            recent:db.prepare('SELECT at,channel,version FROM downloads ORDER BY id DESC LIMIT 20').all(),
            audit:db.prepare('SELECT at,action,target FROM audit ORDER BY id DESC LIMIT 10').all()});
        }
        if(method==='GET' && path==='/api/admin/feedback')return json(res,200,feedbackList(url));
        if(method==='PATCH' && path.startsWith('/api/admin/feedback/')){
          const id=path.slice('/api/admin/feedback/'.length),body=await jsonBody(req);if(!STATUSES.includes(body.status))fail(400,'处理状态无效');
          if(!Number.isSafeInteger(body.revision)||body.revision<0)fail(400,'缺少反馈版本，请刷新后重试');
          const result=db.prepare('UPDATE feedback SET status=?,note=?,revision=revision+1 WHERE id=? AND revision=?').run(body.status,clean(body.note,1000),id,body.revision);
          if(!result.changes){if(!db.prepare('SELECT id FROM feedback WHERE id=?').get(id))fail(404,'反馈不存在');fail(409,'这条反馈已在其他页面更新，当前草稿已保留，请核对后重新读取');}
          audit('feedback_status',id);return json(res,200,{ok:true,revision:body.revision+1});
        }
        if(method==='GET' && path==='/api/admin/export.csv'){
          const rows=feedbackList(url,true).items,keys=['id','at','category','app','version','device','system','detail','contact','diagnostic','status','note'];
          const content='\uFEFF'+[keys.map(csvCell).join(','),...rows.map(row=>keys.map(key=>csvCell(row[key])).join(','))].join('\r\n');
          res.writeHead(200,{'Content-Type':'text/csv; charset=utf-8','Content-Disposition':'attachment; filename="qing-tiao-feedback.csv"'});return res.end(content);
        }
        if(method==='PUT' && path==='/api/admin/policy'){
          const body=await jsonBody(req);if(!['beta','announced'].includes(body.phase))fail(409,'账号和支付尚未接入，不能开启收费；目前仅能保存免费公测或收费预告');
          let download;try{download=new URL(body.downloadUrl);}catch{fail(400,'下载地址无效');}
          if(download.protocol!=='https:'||download.hostname!=='github.com'||download.username||download.password||download.search||download.hash||!download.pathname.startsWith(`/${REPO}/releases/download/`)||!download.pathname.endsWith('.apk'))fail(400,'只允许本项目 GitHub Release 的 HTTPS APK 地址');
          let date=null;if(body.paymentDate){date=clean(body.paymentDate,10);if(!/^\d{4}-\d{2}-\d{2}$/.test(date)||Number.isNaN(Date.parse(date))||new Date(date).toISOString().slice(0,10)!==date)fail(400,'日期格式无效');}
          db.exec('BEGIN');try{set('phase',body.phase);set('announcement',clean(body.announcement,1000));set('paymentDate',date);set('downloadUrl',download.href);audit('policy_update',body.phase);db.exec('COMMIT');}catch(e){db.exec('ROLLBACK');throw e;}
          return json(res,200,policy());
        }
        fail(404,'接口不存在');
      }
      if((method==='GET'||method==='HEAD') && path==='/download'){
        const input=url.searchParams.get('channel') || 'direct',channel=/^[a-z0-9_-]{1,32}$/.test(input)?input:'unknown';
        if(method==='GET'&&!String(req.headers['sec-purpose']||req.headers.purpose||'').includes('prefetch')){
          limit(req,'download',120,60000);db.prepare('INSERT INTO downloads(at,channel,version) VALUES(?,?,?)').run(clock(),channel,version());
        }
        res.writeHead(302,{Location:policy().downloadUrl});return res.end();
      }
      if(method==='POST' && path==='/api/public/feedback'){
        sameOrigin(req);limit(req,'feedback',6);const body=await jsonBody(req);
        if(body.website)fail(400,'提交未通过检查');if(body.consent!==true)fail(400,'请先同意反馈数据处理说明');
        const category=clean(body.category,30),detail=clean(body.detail,2000);
        if(!CATEGORIES.includes(category)||detail.length<4)fail(400,'请选择问题类型并至少填写 4 个字');
        const id='QJ-'+randomUUID(),token=secret();
        db.prepare('INSERT INTO feedback(id,at,category,app,version,device,system,detail,contact,diagnostic,delete_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?)').run(id,clock(),category,clean(body.app,120),clean(body.version,40),clean(body.device,120),clean(body.system,120),detail,clean(body.contact,120),body.includeDiagnostic===true?clean(body.diagnostic,4000):'',digest(token));
        return json(res,201,{id,deleteToken:token,message:'反馈已保存。请保留编号和删除码，以后可自行删除本条反馈。'});
      }
      if(method==='POST' && path==='/api/public/feedback/delete'){
        sameOrigin(req);limit(req,'delete',10);const body=await jsonBody(req);
        const id=clean(body.id,60),hash=digest(clean(body.deleteToken,100));
        if(!db.prepare('SELECT id FROM feedback WHERE id=? AND delete_hash=?').get(id,hash))fail(404,'编号或删除码不正确');
        if(deletionLedger)appendFileSync(deletionLedger,JSON.stringify({id,at:clock()})+'\n',{mode:0o600});
        db.prepare('DELETE FROM feedback WHERE id=? AND delete_hash=?').run(id,hash);return json(res,200,{ok:true});
      }
      const files={'/':'index.html','/feedback':'feedback.html','/admin':'admin.html','/privacy':'privacy.html','/app.js':'app.js','/style.css':'style.css'};
      if((method==='GET'||method==='HEAD')&&files[path]){
        const file=files[path],type=file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html';
        res.writeHead(200,{'Content-Type':`${type}; charset=utf-8`});return res.end(method==='HEAD'?undefined:readFileSync(join(HERE,'public',file)));
      }
      fail(404,'页面不存在');
    }catch(error){if(res.headersSent){res.destroy();return;}json(res,error.status||500,{error:error.status?error.message:'服务暂时不可用，请稍后重试'});}
  });
  server.requestTimeout=15000;server.headersTimeout=10000;
  let closed=false;
  return {server,db,origin,githubSync,async close(){if(closed)return;closed=true;await new Promise(resolve=>server.listening?server.close(resolve):resolve());db.close();},async snapshot(path){await backup(db,path);}};
}
if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  const config=process.env.CONSOLE_ENV || join(HERE,'.env.local');if(existsSync(config))process.loadEnvFile(config);
  const app=createConsole();const port=Number(process.env.PORT||3887),host=process.env.HOST||'127.0.0.1';
  app.server.listen(port,host,()=>console.log(`QingTiao console ready: ${app.origin}/admin`));
  const stop=async()=>{await app.close();process.exit(0);};process.on('SIGINT',stop);process.on('SIGTERM',stop);
}
