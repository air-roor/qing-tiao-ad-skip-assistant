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
  const form=$('feedback-form'),submitButton=form.querySelector('button[type="submit"]');submitButton.textContent='检查反馈内容';
  const steps=el('div',undefined,'form-step');steps.append(el('b','01 填写反馈'),el('span','→ 02 检查并提交 → 03 保存回执'));form.before(steps);
  const counter=el('div','0 / 2000','description-count');counter.id='detail-counter';form.elements.detail.setAttribute('aria-describedby',counter.id);form.elements.detail.closest('label').after(counter);form.elements.detail.addEventListener('input',()=>{counter.textContent=`${form.elements.detail.value.length} / 2000`;});
  const dialog=el('dialog'),title=el('h2','检查后，再确认发送');title.id='feedback-preview-title';dialog.setAttribute('aria-labelledby',title.id);
  const preview=el('pre'),previewMessage=el('p');previewMessage.setAttribute('role','status');
  const actions=el('div',undefined,'actions'),back=el('button','返回修改'),confirm=el('button','确认发送反馈','primary');back.type=confirm.type='button';actions.append(back,confirm);dialog.append(title,el('p','以下是将提交给开发者的全部内容。请删除不愿分享的信息。','muted'),preview,previewMessage,actions);document.body.append(dialog);
  let payload=null,sending=false;
  form.elements.includeDiagnostic.addEventListener('change',()=>{$('diagnostic-field').hidden=!form.elements.includeDiagnostic.checked;});
  form.addEventListener('submit',event=>{
    event.preventDefault();if(!form.reportValidity()||sending)return;
    const data=new FormData(form);payload={...Object.fromEntries(data),includeDiagnostic:data.has('includeDiagnostic'),consent:data.has('consent')};if(!payload.includeDiagnostic)delete payload.diagnostic;
    preview.textContent=[`问题类型：${payload.category}`,`相关应用：${payload.app||'未填写'}`,`轻跳版本：${payload.version||'未填写'}`,`手机：${payload.device||'未填写'}`,`系统：${payload.system||'未填写'}`,`问题描述：\n${payload.detail}`,`联系方式：${payload.contact||'未填写'}`,payload.includeDiagnostic?`主动附带诊断：\n${payload.diagnostic||'未填写'}`:'不附带诊断'].join('\n\n');previewMessage.textContent='';dialog.showModal();
  });
  back.addEventListener('click',()=>dialog.close());dialog.addEventListener('cancel',event=>{if(sending)event.preventDefault();});dialog.addEventListener('close',()=>submitButton.focus());
  confirm.addEventListener('click',async()=>{
    if(!payload||sending)return;sending=true;confirm.disabled=back.disabled=true;previewMessage.textContent='正在发送…';
    try{const result=await api('/api/public/feedback','POST',payload);$('receipt-text').textContent=`反馈编号：${result.id}\n删除码：${result.deleteToken}\n保存时间：${new Date().toLocaleString('zh-CN')}`;$('copy-receipt').textContent='复制编号和删除码';$('receipt').hidden=false;dialog.close();$('receipt').scrollIntoView({behavior:'auto',block:'center'});message('form-message','提交成功，请保存编号和删除码。');form.reset();counter.textContent='0 / 2000';$('diagnostic-field').hidden=true;payload=null;}
    catch(error){previewMessage.textContent=error.message;previewMessage.className='error';}
    finally{sending=false;confirm.disabled=back.disabled=false;}
  });
  $('copy-receipt').addEventListener('click',async()=>{try{await navigator.clipboard.writeText($('receipt-text').textContent);$('copy-receipt').textContent='已复制';}catch{$('copy-receipt').textContent='复制失败，请手动保存上方文字';}});
  $('delete-form').addEventListener('submit',event=>submit(event,async data=>{await api('/api/public/feedback/delete','POST',Object.fromEntries(data));message('delete-message','反馈已删除。');event.target.reset();},'delete-message'));
}
if(page==='admin'){
  const views={overview:['LIVE OVERVIEW','让公测进展，清晰可见。','关注真实数据，优先处理影响使用的问题。'],feedback:['FEEDBACK INBOX','把问题整理好，让修复更快。','按应用、设备、版本和类型定位反馈，保留每一步处理记录。'],promotion:['CHANNEL WORKSPACE','从哪里发现，就从哪里了解。','生成带渠道的下载页入口，查看真实跳转记录。'],settings:['BETA SETTINGS','先说清楚，再开启公测。','公告、版本和后续收费安排集中维护。']};
  let authorized=false,busy=false,policyLoaded=false,policyDirty=false,state=null,view='overview',currentPage=1,totalPages=1,feedbackRequest=0,searchTimer;
  const drafts=new Map(),pending=new Set();
  const feedbackQuery=()=>{const p=new URLSearchParams();for(const [key,value] of [['status',$('feedback-filter').value],['category',$('feedback-category').value],['q',$('feedback-search').value.trim()]])if(value)p.set(key,value);return p;};
  function draftState(){const n=drafts.size;$('draft-state').textContent=n?`${n} 条工单有未保存修改，切换页面仍会保留`:'没有未保存的工单修改';$('draft-state').className=n?'dirty-note':'muted';$('discard-drafts').disabled=!n||pending.size>0;}
  function markPolicy(value){policyDirty=value;$('policy-dirty').textContent=value?'有未保存的设置，自动刷新不会覆盖':'已载入当前设置';$('policy-dirty').className=value?'dirty-note':'muted';}
  function fillPolicy(policy){const form=$('policy-form');for(const key of ['phase','announcement','paymentDate','downloadUrl'])form.elements[key].value=policy[key]||'';policyLoaded=true;markPolicy(false);}
  function locked(){authorized=false;$('workspace').hidden=true;$('login').hidden=false;$('logout').hidden=true;policyLoaded=false;policyDirty=false;state=null;drafts.clear();$('feedback-list').replaceChildren();}
  function unlock(){authorized=true;$('workspace').hidden=false;$('login').hidden=true;$('logout').hidden=false;}
  function selectView(name){
    view=views[name]?name:'overview';for(const panel of document.querySelectorAll('[data-panel]'))panel.hidden=panel.dataset.panel!==view;
    for(const button of document.querySelectorAll('.workspace-nav [data-view]')){button.classList.toggle('active',button.dataset.view===view);if(button.dataset.view===view)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');}
    $('view-kicker').textContent=views[view][0];const titleParts=views[view][1].split('，');$('view-title').replaceChildren(document.createTextNode(titleParts[0]+'，'),el('em',titleParts[1]));$('view-subtitle').textContent=views[view][2];
    if(location.hash!=='#'+view)history.replaceState(null,'','#'+view);
    if(authorized&&view==='feedback')loadFeedback(true).catch(handleError);
  }
  function handleError(error){if(error.status===401)locked();else message(authorized?'sync-state':'login-message',error.message,true);}
  function tableRows(id,rows){$(id).replaceChildren();for(const cells of rows){const row=el('tr');for(const value of cells)row.append(el('td',String(value)));$(id).append(row);}}
  function trend(data,asOf){
    const map=new Map(data.map(item=>[item.day,item.count])),max=Math.max(1,...map.values());$('trend').replaceChildren();
    for(let i=13;i>=0;i--){const day=new Date(asOf-i*86400000+8*3600000).toISOString().slice(0,10),count=map.get(day)||0,item=el('div',undefined,'trend-item');item.title=`${day}：${count} 次入口跳转`;item.setAttribute('aria-label',item.title);item.append(el('b',String(count)),el('div',undefined,`bar b${count?Math.max(1,Math.round(count/max*10)):0}`),el('small',day.slice(5)));$('trend').append(item);}
    $('trend-empty').textContent=data.length?'入口跳转按北京时间归日。':'还没有本站入口跳转记录，分享部署后的渠道链接即可开始记录。';
  }
  function channelLink(){
    const code=$('channel-code').value.trim(),valid=/^[a-z0-9_-]{1,32}$/.test(code);$('copy-channel').disabled=!valid;
    if(!valid){$('channel-link').value='';message('channel-message','请输入 1–32 位小写字母、数字、下划线或短横线。',true);return;}
    const origin=state?.policy.publicOrigin||location.origin;$('channel-link').value=origin+'/?channel='+encodeURIComponent(code);
    const local=['127.0.0.1','localhost','[::1]'].includes(new URL(origin).hostname);message('channel-message',local?'当前是本机地址，只有这台电脑可访问；部署 HTTPS 域名后再对外分享。':'复制此下载页链接到对应渠道，点击下载后才会记录入口跳转。');
  }
  function renderOverview(data){
    state=data;$('metric-github').textContent=data.github.total??'—';$('metric-today').textContent=data.totals.today;$('metric-total').textContent=data.totals.redirects;$('metric-feedback').textContent=data.totals.newFeedback;$('feedback-total').textContent=`当前保留反馈 ${data.totals.feedback} 条`;$('nav-feedback-count').textContent=data.totals.newFeedback>99?'99+':data.totals.newFeedback;
    $('sync-state').textContent=`● 更新于 ${time(data.asOf)} · GitHub 同步于 ${time(data.github.updatedAt)}`;$('sync-state').className='live';
    $('github-status').textContent=data.github.error?`同步失败：${data.github.error}。保留上次成功数据，不代表零下载。`:`来源：GitHub Release assets · 最近同步 ${time(data.github.updatedAt)}`;
    tableRows('releases',data.github.assets.map(item=>[item.version,item.name,item.downloads,`${(item.size/1024).toFixed(1)} KB`]));trend(data.trend,data.asOf);$('channels').replaceChildren();
    for(const item of data.channels){const row=el('div',undefined,'channel');row.append(el('span',item.channel),el('b',String(item.count)));$('channels').append(row);}if(!data.channels.length)$('channels').append(el('p','暂无渠道入口记录。','empty'));
    $('feedback-progress').replaceChildren();const counts=new Map(data.feedbackByStatus.map(item=>[item.status,item.count]));for(const [key,text] of Object.entries(label)){const cell=el('div');cell.append(el('span',text),el('strong',String(counts.get(key)||0)));$('feedback-progress').append(cell);}
    tableRows('recent-downloads',data.recent.map(item=>[time(item.at),item.channel,item.version]));$('recent-empty').textContent=data.recent.length?'显示最近 20 次入口跳转。':'还没有本站下载入口活动。';
    $('nav-location').textContent=['127.0.0.1','localhost','[::1]'].includes(new URL(data.policy.publicOrigin||location.origin).hostname)?'本机工作台':'在线工作台';$('policy-notice').textContent=data.policy.notice;if(!policyLoaded)fillPolicy(data.policy);
    $('deployment-state').textContent=['127.0.0.1','localhost','[::1]'].includes(new URL(data.policy.publicOrigin||location.origin).hostname)?'当前运行在本机，其他用户无法访问这个地址。部署正式域名后，再配置 App 的网页反馈入口。':'站点地址：'+data.policy.publicOrigin+'。请从手机网络验证下载、反馈和登录权限。';channelLink();
  }
  async function loadFeedback(force=false){
    if(!authorized)return;if(!force&&$('feedback-list').contains(document.activeElement))return;
    const query=feedbackQuery(),filterKey=query.toString();$('export').href='/api/admin/export.csv'+(filterKey?'?'+filterKey:'');query.set('page',currentPage);query.set('pageSize',$('page-size').value);
    const request=++feedbackRequest,result=await api('/api/admin/feedback?'+query.toString());if(request!==feedbackRequest||filterKey!==feedbackQuery().toString()||(!force&&$('feedback-list').contains(document.activeElement)))return;
    currentPage=result.page;totalPages=result.totalPages;$('page-info').textContent=`第 ${result.page} / ${result.totalPages} 页`;$('page-prev').disabled=result.page<=1;$('page-next').disabled=result.page>=result.totalPages;$('feedback-summary').textContent=`共 ${result.total} 条符合条件，当前页 ${result.items.length} 条`;
    $('feedback-list').replaceChildren();
    if(!result.items.length){const empty=el('div',undefined,'empty-state');empty.append(el('h3','这里暂时没有反馈'),el('p',filterKey?'试着调整筛选，或清除关键词。':'用户在网页提交后，会出现在这里；不使用演示数据填充。'));$('feedback-list').append(empty);draftState();return;}
    for(const item of result.items){
      const card=el('article',undefined,'feedback-item'),heading=el('div',undefined,'feedback-title'),pill=el('span',label[item.status],'status-pill');pill.dataset.status=item.status;heading.append(el('h3',`${item.category} · ${item.app||'未填写应用'}`),pill);card.append(heading,el('div',`${time(item.at)} · ${item.id}\n${item.version||'版本未填'} · ${item.device||'型号未填'} · ${item.system||'系统未填'}`,'feedback-meta'),el('p',item.detail,'detail'));
      if(item.contact)card.append(el('p','自愿联系方式：'+item.contact,'feedback-meta'));if(item.diagnostic){const details=el('details');details.append(el('summary','用户主动附带的诊断'),el('pre',item.diagnostic));card.append(details);}
      const controls=el('div',undefined,'actions'),status=el('select');status.setAttribute('aria-label','处理状态');for(const [value,text] of Object.entries(label)){const option=el('option',text);option.value=value;status.append(option);}
      const note=el('textarea');note.maxLength=1000;note.placeholder='内部备注：复现步骤、修复版本或后续安排';note.setAttribute('aria-label','内部处理备注');const draft=drafts.get(item.id);status.value=draft?.status??item.status;note.value=draft?.note??item.note;
      const save=el('button',draft?'保存修改':'暂无修改'),discard=el('button','放弃本条修改'),resultText=el('span');save.type=discard.type='button';save.disabled=!draft||pending.has(item.id);status.disabled=note.disabled=discard.disabled=pending.has(item.id);discard.hidden=!draft;resultText.setAttribute('role','status');
      const keep=()=>{if(status.value===item.status&&note.value===item.note){drafts.delete(item.id);save.disabled=true;discard.hidden=true;save.textContent='暂无修改';}else{const previous=drafts.get(item.id);drafts.set(item.id,{status:status.value,note:note.value,revision:previous?.revision??item.revision});save.disabled=false;discard.hidden=false;save.textContent='保存修改';}draftState();};status.addEventListener('change',keep);note.addEventListener('input',keep);
      discard.addEventListener('click',()=>{if(confirm('放弃这条工单未保存的修改，并读取服务器记录？')){drafts.delete(item.id);draftState();loadFeedback(true).catch(handleError);}});
      save.addEventListener('click',async()=>{const value=drafts.get(item.id);if(!value||pending.has(item.id))return;pending.add(item.id);save.disabled=status.disabled=note.disabled=discard.disabled=true;draftState();try{await api('/api/admin/feedback/'+encodeURIComponent(item.id),'PATCH',value);pending.delete(item.id);drafts.delete(item.id);draftState();message('feedback-action-message','工单修改已保存。');await loadFeedback(true);}catch(error){resultText.textContent=error.message;resultText.className=error.status===409?'conflict':'error';}finally{pending.delete(item.id);status.disabled=note.disabled=discard.disabled=false;save.disabled=!drafts.has(item.id);draftState();}});
      controls.append(status,save,discard,resultText);card.append(note,controls);$('feedback-list').append(card);
    }
    draftState();
  }
  async function refresh(){if(busy)return;busy=true;$('refresh').disabled=true;try{const data=await api('/api/admin/overview');unlock();renderOverview(data);if(view==='feedback')await loadFeedback();}catch(error){handleError(error);}finally{busy=false;$('refresh').disabled=false;}}
  function filtersChanged(){currentPage=1;loadFeedback(true).catch(handleError);}
  for(const button of document.querySelectorAll('[data-view]'))button.addEventListener('click',()=>selectView(button.dataset.view));
  $('login-form').addEventListener('submit',event=>submit(event,async data=>{await api('/api/admin/login','POST',{password:data.get('password')});$('login-form').reset();await refresh();},'login-message'));
  $('logout').addEventListener('click',async()=>{if((drafts.size||policyDirty)&&!confirm('仍有未保存的修改，确定退出并放弃草稿？'))return;try{await api('/api/admin/logout','POST',{});locked();}catch(error){handleError(error);}});
  $('refresh').addEventListener('click',refresh);$('feedback-filter').addEventListener('change',filtersChanged);$('feedback-category').addEventListener('change',filtersChanged);$('page-size').addEventListener('change',filtersChanged);
  $('feedback-search').addEventListener('input',()=>{clearTimeout(searchTimer);searchTimer=setTimeout(filtersChanged,250);});
  $('reset-filters').addEventListener('click',()=>{$('feedback-search').value='';$('feedback-filter').value='';$('feedback-category').value='';filtersChanged();});
  $('discard-drafts').addEventListener('click',()=>{if(confirm('放弃全部未保存的工单修改？不会删除服务器上的反馈。')){drafts.clear();draftState();loadFeedback(true).catch(handleError);}});
  $('page-prev').addEventListener('click',()=>{if(currentPage>1){currentPage--;loadFeedback(true).catch(handleError);}});$('page-next').addEventListener('click',()=>{if(currentPage<totalPages){currentPage++;loadFeedback(true).catch(handleError);}});
  $('channel-code').addEventListener('input',channelLink);for(const button of document.querySelectorAll('[data-channel]'))button.addEventListener('click',()=>{$('channel-code').value=button.dataset.channel;channelLink();});
  $('copy-channel').addEventListener('click',async()=>{try{await navigator.clipboard.writeText($('channel-link').value);message('channel-message','已复制。'+(['127.0.0.1','localhost'].includes(location.hostname)?'当前仍是本机地址，部署域名后再对外分享。':''));}catch{message('channel-message','无法自动复制，请手动复制上方链接。',true);}});
  $('policy-form').addEventListener('input',()=>markPolicy(true));$('policy-form').addEventListener('change',()=>markPolicy(true));$('reset-policy').addEventListener('click',()=>{if(state&&(!policyDirty||confirm('放弃未保存的设置，恢复服务器当前记录？')))fillPolicy(state.policy);});
  $('policy-form').addEventListener('submit',event=>submit(event,async data=>{const fields=[...$('policy-form').elements];fields.forEach(field=>field.disabled=true);try{const policy=await api('/api/admin/policy','PUT',Object.fromEntries(data));fillPolicy(policy);message('policy-message','规则与公告已保存。');await refresh();}finally{fields.forEach(field=>field.disabled=false);}},'policy-message'));
  window.addEventListener('beforeunload',event=>{if(authorized&&(drafts.size||policyDirty)){event.preventDefault();event.returnValue='';}});
  window.addEventListener('hashchange',()=>selectView(location.hash.slice(1)));
  selectView(location.hash.slice(1));await refresh();setInterval(()=>{if(authorized&&!document.hidden)refresh();},10000);document.addEventListener('visibilitychange',()=>{if(authorized&&!document.hidden)refresh();});
}
