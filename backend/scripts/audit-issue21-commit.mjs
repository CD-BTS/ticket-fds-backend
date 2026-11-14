// Offline audit only. Accept optional {secrets:[{source,value}]} via stdin; never print values.
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {gunzipSync} from 'node:zlib';
const git = args => {
    const r=spawnSync('git',args,{encoding:'utf8',maxBuffer:32*1024*1024,shell:false});
    if(r.status!==0) throw new Error(`Git read failed: ${args[0]}`);
    return r.stdout;
};
const envelope=JSON.parse(fs.readFileSync(0,'utf8').replace(/^\uFEFF/,''));
const secrets=(envelope.secrets??[]).filter(x=>typeof x.value==='string'&&x.value.length>=4);
const findings=[];
const rules=[
    ['GitHub token',/\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{30,})\b/g],
    ['AWS access key',/\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/g],
    ['Private key',/^-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----$/gm],
    ['Credential-bearing URL',/\b(?:mysql|postgres(?:ql)?|https?):\/\/[^\s/@:]+:[^\s/@]+@/g],
];
const escape=s=>s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
function scan(text,file,scope,embedded=false){
    for(const secret of secrets){
        const jsonEscaped=JSON.stringify(secret.value).slice(1,-1);
        const matched=secret.value.length>=12?(text.includes(secret.value)||text.includes(jsonEscaped)||text.includes(encodeURIComponent(secret.value))):new RegExp(`(?:["']${escape(secret.value)}["']|=${escape(secret.value)}(?:\\r?\\n|$)|:${escape(secret.value)}@)`).test(text);
        if(matched)findings.push({scope,file,embedded,kind:'known-secret',source:secret.source});
    }
    for(const [kind,re] of rules){re.lastIndex=0;if(re.test(text))findings.push({scope,file,embedded,kind});}
    if(!embedded&&file.endsWith('.html')){
        const encoded=text.match(/<script id="data"[^>]*>([\s\S]*?)<\/script>/)?.[1];
        if(encoded)scan(gunzipSync(Buffer.from(encoded.trim(),'base64')).toString(),file,scope,true);
    }
}
const indexed=git(['ls-files','--stage','-z']).split('\0').filter(Boolean).map(line=>{
    const tab=line.indexOf('\t');const [mode,hash,stage]=line.slice(0,tab).split(' ');
    return {mode,hash,stage,file:line.slice(tab+1)};
});
let indexFiles=0;
for(const item of indexed){
    if(item.stage!=='0')throw new Error('Unmerged index; cannot audit');
    const content=git(['cat-file','blob',item.hash]);
    if(!content.includes('\0')){scan(content,item.file,'index');indexFiles++;}
}
const changed=git(['diff','--name-only','-z','HEAD']).split('\0').filter(Boolean);
const untracked=git(['ls-files','--others','--exclude-standard','-z']).split('\0').filter(Boolean);
const extras=fs.existsSync('docs/issue21-artifacts')?fs.readdirSync('docs/issue21-artifacts').map(n=>'docs/issue21-artifacts/'+n):[];
const working=[...new Set([...changed,...untracked,...extras])].filter(file=>fs.existsSync(file)&&fs.statSync(file).isFile());
let workingFiles=0;
for(const file of working){const bytes=fs.readFileSync(file);if(!bytes.includes(0)){scan(bytes.toString('utf8'),file,'working-tree');workingFiles++;}}
const userIdFile='load-tests/issue21-user-ids.txt';
const userIds=fs.existsSync(userIdFile)?fs.readFileSync(userIdFile,'utf8').trim().split(',').filter(Boolean):[];
const userIdExposure=[];
for(const item of indexed){
    const content=git(['cat-file','blob',item.hash]);
    if(userIds.some(id=>content.includes(id)))userIdExposure.push(item.file);
}
const changes=git(['diff','--cached','--name-only','-z']).split('\0').filter(Boolean);
const drift=git(['diff','--name-only','-z']).split('\0').filter(Boolean).filter(file=>changes.includes(file));
const ignored=spawnSync('git',['check-ignore','.env','.env.issue21-key.dpapi','.env.issue21-server.dpapi','load-tests/issue21-user-ids.txt','thread-dump.txt'],{encoding:'utf8',shell:false}).stdout.trim().split(/\r?\n/).filter(Boolean);
const report={generatedAt:new Date().toISOString(),indexFilesScanned:indexFiles,changedWorkingFilesScanned:workingFiles,knownSecretCount:secrets.length,
    dashboardPayloadsDecoded:true,stagedPaths:changes,indexWorkingTreeDrift:drift,findings,indexUserIdExposure:userIdExposure,
    localUserIdCount:userIds.length,ignoredPaths:ignored,
    notes:['Values are never printed or persisted. Exact matching covers supplied live credentials and auth hashes plus common token/private-key/credential-URL patterns.','Both current index blobs and changed/untracked working files are scanned, including decoded gzip/base64 dashboard data.','This is a point-in-time content check, not a guarantee about future staging, Git history, external archives or unknown secret formats.','No index entries, server settings, DB records or credentials were modified.']};
fs.mkdirSync('docs/issue21-artifacts',{recursive:true});
fs.writeFileSync('docs/issue21-artifacts/git-security-audit.json',JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({indexFiles,workingFiles,knownSecretCount:secrets.length,findings,indexUserIdExposure:userIdExposure,indexWorkingTreeDrift:drift},null,2));
if(findings.some(f=>f.scope==='index')||userIdExposure.length)process.exitCode=2;
