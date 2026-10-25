import { DatabaseSync, backup } from 'node:sqlite';
import { existsSync, mkdirSync, chmodSync } from 'node:fs';
import { dirname,join,resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const dir=dirname(fileURLToPath(import.meta.url));const env=process.env.CONSOLE_ENV||join(dir,'.env.local');if(existsSync(env))process.loadEnvFile(env);
const folder=resolve(dir,'backups');mkdirSync(folder,{recursive:true,mode:0o700});const target=join(folder,'console-'+new Date().toISOString().replaceAll(':','-')+'.sqlite');
const db=new DatabaseSync(resolve(dir,process.env.DATA_DIR||'data','console.sqlite'),{readOnly:true});
try{await backup(db,target);chmodSync(target,0o600);console.log('Backup created: '+target);}finally{db.close();}
