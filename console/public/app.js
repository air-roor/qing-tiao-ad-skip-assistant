const $=id=>document.getElementById(id);
const page=document.body.dataset.page;
const label={new:'待处理',investigating:'处理中',fixed:'已修复',closed:'已关闭'};
const time=value=>value?new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false}):'尚未同步';
const el=(tag,text,className)=>{const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(className)node.className=className;return node;};
const message=(id,text,error=false)=>{const node=$(id);node.textContent=text;node.className=error?'error':'success';};
async function api(path,method='GET',body){
  const response=await fetch(path,{method,credentials:'same-origin',headers:body?{'Content-Type':'application/json'}:{},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(25000)});
  const data=await response.json();if(!response.ok){const error=new Error(data.error||'请求失败');error.status=response.status;throw error;}return data;
}
async function submit(event,action,messageId){
  event.preventDefault();const button=event.target.querySelector('button[type="submit"]');if(button)button.disabled=true;
  try{await action(new FormData(event.target));}catch(error){message(messageId,error.message||'请求失败，请稍后重试',true);}finally{if(button)button.disabled=false;}
}
if(page==='landing'){
  const requested=new URL(location.href).searchParams.get('channel')||'direct';const channel=/^[a-z0-9_-]{1,32}$/.test(requested)?requested:'direct';
  $('download').href='/download?channel='+encodeURIComponent(channel);
  api('/api/public/config').then(policy=>{$('notice').textContent=policy.notice;$('announcement').textContent=[policy.announcement,policy.paymentDate?'计划收费日期：'+policy.paymentDate+'（当前未开放付款）':''].filter(Boolean).join('\n');}).catch(()=>{$('announcement').textContent='公告暂时加载失败，当前页面不接受付款。';});
}
if(page==='feedback'){
  const form=$('feedback-form');
  form.elements.includeDiagnostic.addEventListener('change',()=>{$('diagnostic-field').hidden=!form.elements.includeDiagnostic.checked;});
  form.addEventListener('submit',event=>submit(event,async data=>{
    const body=Object.fromEntries(data);body.includeDiagnostic=data.has('includeDiagnostic');body.consent=data.has('consent');
    const result=await api('/api/public/feedback','POST',body);
    $('receipt-text').textContent=`反馈编号：${result.id}\n删除码：${result.deleteToken}\n保存时间：${new Date().toLocaleString('zh-CN')}`;
    $('receipt').hidden=false;$('receipt').scrollIntoView({behavior:'auto',block:'center'});message('form-message','提交成功，请保存编号和删除码。');form.reset();$('diagnostic-field').hidden=true;
  },'form-message'));
  $('copy-receipt').addEventListener('click',async()=>{try{await navigator.clipboard.writeText($('receipt-text').textContent);$('copy-receipt').textContent='已复制';}catch{$('copy-receipt').textContent='复制失败，请手动保存上方文字';}});
  $('delete-form').addEventListener('submit',event=>submit(event,async data=>{await api('/api/public/feedback/delete','POST',Object.fromEntries(data));message('delete-message','反馈已删除。');event.target.reset();},'delete-message'));
}
if(page==='admin'){
  let authorized=false,busy=false,policyLoaded=false;
  const drafts=new Map();
  function locked(){authorized=false;$('workspace').hidden=true;$('login').hidden=false;$('logout').hidden=true;policyLoaded=false;drafts.clear();$('feedback-list').replaceChildren();}
  function unlock(){authorized=true;$('workspace').hidden=false;$('login').hidden=true;$('logout').hidden=false;}
  function tableRows(items){$('releases').replaceChildren();for(const item of items){const row=el('tr');for(const value of [item.version,item.name,item.downloads,`${(item.size/1024).toFixed(1)} KB`])row.append(el('td',String(value)));$('releases').append(row);}}
  function trend(data,asOf){
    const map=new Map(data.map(item=>[item.day,item.count]));const max=Math.max(1,...map.values());$('trend').replaceChildren();
    for(let i=13;i>=0;i--){const day=new Date(asOf-i*86400000+8*3600000).toISOString().slice(0,10),count=map.get(day)||0;
      const item=el('div',undefined,'trend-item');item.title=`${day}：${count} 次入口跳转`;item.setAttribute('aria-label',item.title);
      item.append(el('b',String(count)),el('div',undefined,`bar b${count?Math.max(1,Math.round(count/max*10)):0}`),el('small',day.slice(5)));$('trend').append(item);
    }
    $('trend-empty').textContent=data.length?'入口跳转按北京时间归日。':'还没有本站入口跳转记录。';
  }
  function renderOverview(data){
    $('metric-github').textContent=data.github.total??'—';$('metric-today').textContent=data.totals.today;$('metric-total').textContent=data.totals.redirects;$('metric-feedback').textContent=data.totals.newFeedback;$('feedback-total').textContent=`当前保留反馈 ${data.totals.feedback} 条`;
    $('sync-state').textContent=`● 后台更新：${time(data.asOf)} · GitHub 同步：${time(data.github.updatedAt)}`;$('sync-state').className='live';
    $('github-status').textContent=data.github.error?`同步失败：${data.github.error}。显示上次成功数据（如有），不是零下载。`:`数据来源：GitHub Release assets · 最近同步 ${time(data.github.updatedAt)}`;
    tableRows(data.github.assets);trend(data.trend,data.asOf);$('channels').replaceChildren();
    for(const item of data.channels){const row=el('div',undefined,'channel');row.append(el('span',item.channel),el('b',String(item.count)));$('channels').append(row);}
    if(!data.channels.length)$('channels').append(el('p','暂无渠道数据。','empty'));
    $('policy-notice').textContent=data.policy.notice;
    if(!policyLoaded){const form=$('policy-form');for(const key of ['phase','announcement','paymentDate','downloadUrl'])form.elements[key].value=data.policy[key]||'';policyLoaded=true;}
  }
  async function feedback(){
    const filter=$('feedback-filter').value,query=filter?'?status='+encodeURIComponent(filter):'';
    $('export').href='/api/admin/export.csv'+query;
    const result=await api('/api/admin/feedback'+query);if(filter!==$('feedback-filter').value)return;const focused=$('feedback-list').contains(document.activeElement);
    if(focused)return;
    $('feedback-list').replaceChildren();if(!result.items.length){$('feedback-list').append(el('p','当前筛选下没有反馈。','empty'));return;}
    for(const item of result.items){
      const card=el('article',undefined,'feedback-item');card.append(el('h3',`${item.category} · ${item.app||'未填写应用'}`),el('div',`${time(item.at)} · ${item.id} · ${item.version||'版本未填'} · ${item.device||'型号未填'} · ${item.system||'系统未填'}`,'feedback-meta'),el('p',item.detail));
      if(item.contact)card.append(el('p','自愿联系方式：'+item.contact,'feedback-meta'));
      if(item.diagnostic){const details=el('details');details.append(el('summary','查看用户主动附带的诊断'),el('pre',item.diagnostic));card.append(details);}
      const controls=el('div',undefined,'actions'),status=el('select');status.setAttribute('aria-label','处理状态');
      for(const [value,text] of Object.entries(label)){const option=el('option',text);option.value=value;status.append(option);}
      const note=el('textarea');note.maxLength=1000;note.placeholder='内部处理备注，仅管理员可见';note.setAttribute('aria-label','内部处理备注');const draft=drafts.get(item.id);status.value=draft?.status??item.status;note.value=draft?.note??item.note;
      const keep=()=>drafts.set(item.id,{status:status.value,note:note.value});status.addEventListener('change',keep);note.addEventListener('input',keep);
      const save=el('button','保存处理状态');save.type='button';const resultText=el('span');resultText.setAttribute('role','status');
      save.addEventListener('click',async()=>{save.disabled=true;try{await api('/api/admin/feedback/'+encodeURIComponent(item.id),'PATCH',{status:status.value,note:note.value});drafts.delete(item.id);resultText.textContent='已保存';}catch(error){resultText.textContent=error.message;}finally{save.disabled=false;}});
      controls.append(status,save,resultText);card.append(note,controls);$('feedback-list').append(card);
    }
  }
  async function refresh(){
    if(busy)return;busy=true;
    try{const data=await api('/api/admin/overview');unlock();renderOverview(data);await feedback();}
    catch(error){if(error.status===401)locked();else message(authorized?'sync-state':'login-message',error.message,true);}finally{busy=false;}
  }
  $('login-form').addEventListener('submit',event=>submit(event,async data=>{await api('/api/admin/login','POST',{password:data.get('password')});$('login-form').reset();await refresh();},'login-message'));
  $('logout').addEventListener('click',async()=>{try{await api('/api/admin/logout','POST',{});locked();}catch(error){message('sync-state',error.message,true);}});
  $('refresh').addEventListener('click',refresh);$('feedback-filter').addEventListener('change',()=>feedback().catch(error=>message('sync-state',error.message,true)));
  $('policy-form').addEventListener('submit',event=>submit(event,async data=>{await api('/api/admin/policy','PUT',Object.fromEntries(data));message('policy-message','规则与公告已保存，下载页会显示最新内容。');await refresh();},'policy-message'));
  await refresh();setInterval(()=>{if(authorized&&!document.hidden)refresh();},10000);document.addEventListener('visibilitychange',()=>{if(authorized&&!document.hidden)refresh();});
}
