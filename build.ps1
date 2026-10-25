param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = "$env:USERPROFILE\.jdks\microsoft-jdk-21",
    [string]$Platform = 'android-36.1',
    [string]$BuildTools = '36.1.0'
)
$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$out = Join-Path $project 'build'
$bt = Join-Path $Sdk "build-tools\$BuildTools"
$androidJar = Join-Path $Sdk "platforms\$Platform\android.jar"
$java = Join-Path $Jdk 'bin\java.exe'
$javac = Join-Path $Jdk 'bin\javac.exe'
$jar = Join-Path $Jdk 'bin\jar.exe'
$keytool = Join-Path $Jdk 'bin\keytool.exe'
foreach ($file in @($java,$javac,$androidJar,"$bt\aapt2.exe","$bt\lib\d8.jar","$bt\lib\apksigner.jar")) {
    if (!(Test-Path -LiteralPath $file)) { throw "Missing build dependency: $file" }
}
function Run([string]$File, [string[]]$Arguments) {
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $File" }
}
# Unique intermediates keep previous outputs intact and prevent stale classes entering an APK.
$run = Join-Path $out ('run-' + [DateTime]::Now.ToString('yyyyMMdd-HHmmss-fff'))
foreach($part in @('classes','generated','dex','tests')) { New-Item -ItemType Directory -Force -Path (Join-Path $run $part) | Out-Null }
$source = Join-Path $project 'app\src\main'
Run "$bt\aapt2.exe" @('compile','--dir',"$source\res",'-o',"$run\resources.zip")
Run "$bt\aapt2.exe" @('link','-o',"$run\base.apk",'-I',$androidJar,'--manifest',"$source\AndroidManifest.xml",'--java',"$run\generated",'--version-code','15','--version-name','0.3.0-beta.4',"$run\resources.zip")
$sources = @((Get-ChildItem "$source\java","$run\generated" -Recurse -Filter '*.java').FullName)
Run $javac (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$androidJar,'-d',"$run\classes") + $sources)
Run $javac @('-encoding','UTF-8','-d',"$run\tests","$source\java\cn\local\adskip\Rules.java","$source\java\cn\local\adskip\Feedback.java","$project\tests\RulesTest.java")
Run $java @('-cp',"$run\tests",'cn.local.adskip.RulesTest')
Run $jar @('cf',"$run\classes.jar",'-C',"$run\classes",'.')
Run $java @('-cp',"$bt\lib\d8.jar",'com.android.tools.r8.D8','--lib',$androidJar,'--min-api','26','--output',"$run\dex","$run\classes.jar")
Run $jar @('uf',"$run\base.apk",'-C',"$run\dex",'classes.dex')
Run "$bt\zipalign.exe" @('-f','4',"$run\base.apk","$run\aligned.apk")
$key = Join-Path $out 'local-debug.jks'
if (!(Test-Path -LiteralPath $key)) {
    Run $keytool @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Local Debug,O=Ad Skip Prototype,C=CN')
}
$apk = Join-Path $out 'ad-skip-assistant-debug.apk'
Run $java @('-jar',"$bt\lib\apksigner.jar",'sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,"$run\aligned.apk")
Run $java @('-jar',"$bt\lib\apksigner.jar",'verify','--verbose',$apk)
Run "$bt\zipalign.exe" @('-c','4',$apk)
Get-FileHash -LiteralPath $apk -Algorithm SHA256 | Format-List
Write-Output "APK: $apk"
