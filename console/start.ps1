param([switch]$OpenBrowser)
$ErrorActionPreference='Stop'
$consoleRoot=$PSScriptRoot
$nodeRuntime='C:/Users/33925/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
if(!(Test-Path -LiteralPath $nodeRuntime)){$nodeRuntime=(Get-Command node -ErrorAction Stop).Source}
Push-Location $consoleRoot
try {
 & $nodeRuntime setup.mjs
 if($LASTEXITCODE -ne 0){throw 'Setup failed'}
 $publicOrigin='http://127.0.0.1:3887'
 $originLine=Get-Content -LiteralPath '.env.local' | Where-Object {$_ -match '^PUBLIC_ORIGIN='} | Select-Object -First 1
 if($originLine){$publicOrigin=$originLine.Substring('PUBLIC_ORIGIN='.Length).Trim()}
 if(([Uri]$publicOrigin).Host -notin @('127.0.0.1','localhost')){throw 'Use the service manager for public deployments; this launcher is for the local console.'}
 $ready=$false
 try {$probe=Invoke-RestMethod -Uri ($publicOrigin+'/health') -TimeoutSec 2;$ready=$probe.service -eq 'qing-tiao-console'}catch{}
 if(!$ready){
   $process=Start-Process -FilePath $nodeRuntime -ArgumentList 'server.mjs' -WorkingDirectory $consoleRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $consoleRoot 'server.stdout.log') -RedirectStandardError (Join-Path $consoleRoot 'server.stderr.log') -PassThru
   $process.Id | Set-Content -LiteralPath 'data/server.pid'
   for($attempt=0;$attempt -lt 15;$attempt++){
     Start-Sleep -Milliseconds 200
     try {$probe=Invoke-RestMethod -Uri ($publicOrigin+'/health') -TimeoutSec 1;if($probe.service -eq 'qing-tiao-console'){$ready=$true;break}}catch{}
   }
   if(!$ready){throw 'Console did not become ready. See console/server.stderr.log.'}
 }
 Write-Output ('Console ready: '+$publicOrigin+'/admin')
 if($OpenBrowser){Start-Process ($publicOrigin+'/admin')}
} finally {Pop-Location}
