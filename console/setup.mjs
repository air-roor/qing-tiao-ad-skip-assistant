import { mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { hashPassword, secret } from './security.mjs';
const dir=dirname(fileURLToPath(import.meta.url)),config=join(dir,'.env.local');
if(existsSync(config)){console.log('Configuration exists; left unchanged.');process.exit(0);}
const password=secret().slice(0,24),hash=await hashPassword(password);
mkdirSync(join(dir,'data'),{recursive:true,mode:0o700});
writeFileSync(config,`HOST=127.0.0.1\nPORT=3887\nPUBLIC_ORIGIN=http://127.0.0.1:3887\nADMIN_PASSWORD_HASH=${hash}\nDATA_DIR=data\n`,{mode:0o600,flag:'wx'});
writeFileSync(join(dir,'data','首次登录说明.txt'),`轻跳公测后台（只在本机运行）\r\n\r\n后台：http://127.0.0.1:3887/admin\r\n密码：${password}\r\n\r\n密码不会显示在终端或聊天中。请把它保存在自己的密码管理器中。\r\n公开部署前必须使用 HTTPS 并重新设置密码；不要上传此文件或 .env.local。\r\n`,{mode:0o600,flag:'wx'});
console.log('Created local configuration. Login password is in console/data/首次登录说明.txt; it is not printed.');
