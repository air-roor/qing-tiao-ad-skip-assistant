$ErrorActionPreference='Stop'
$pidFile=Join-Path $PSScriptRoot 'data/server.pid'
if(!(Test-Path -LiteralPath $pidFile)){Write-Output 'No local console PID file. Use the service manager if started elsewhere.';exit 0}
$consolePid=[int](Get-Content -LiteralPath $pidFile -Raw).Trim()
$process=Get-CimInstance Win32_Process -Filter "ProcessId = $consolePid"
if(!$process){Write-Output 'Console is already stopped.';exit 0}
if($process.Name -ne 'node.exe' -or $process.CommandLine -notmatch '(?:^|\s|"|\\)server\.mjs(?:\s|"|$)'){throw 'PID is not the expected console process; left untouched.'}
$originLine=Get-Content -LiteralPath (Join-Path $PSScriptRoot '.env.local') | Where-Object {$_ -match '^PUBLIC_ORIGIN='} | Select-Object -First 1
$origin=$originLine.Substring('PUBLIC_ORIGIN='.Length).Trim()
if(([Uri]$origin).Host -notin @('127.0.0.1','localhost')){throw 'This stop helper is only for the local console.'}
$health=Invoke-RestMethod -Uri ($origin+'/health') -TimeoutSec 2
$listener=Get-NetTCPConnection -LocalPort ([Uri]$origin).Port -State Listen -ErrorAction Stop
if($health.service -ne 'qing-tiao-console' -or !($listener | Where-Object OwningProcess -eq $consolePid)){throw 'Port owner does not match the console PID; left untouched.'}
Stop-Process -Id $consolePid -ErrorAction Stop
Write-Output 'Local console stopped. Data has been retained.'
