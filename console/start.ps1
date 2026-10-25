$ErrorActionPreference='Stop'
$consoleRoot=$PSScriptRoot
$nodeRuntime='C:/Users/33925/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
if(!(Test-Path -LiteralPath $nodeRuntime)){$nodeRuntime=(Get-Command node -ErrorAction Stop).Source}
Push-Location $consoleRoot
try {
 & $nodeRuntime setup.mjs
 if($LASTEXITCODE -ne 0){throw 'Setup failed'}
 & $nodeRuntime server.mjs
} finally {Pop-Location}
