# AIDL ko ASLI aidl compiler se jaanchta hai, build chalane se pehle.
#
# Ye isliye bani ki ek AIDL galti seedha user ki build mein pakdi gayi:
#
#     IShellService.aidl:21.9-17: You must either assign id's to all methods
#     or to none of them.
#
# Kotlin wala check ise kabhi nahi pakad sakta - wo generated Java dekhta hai,
# .aidl file ko chhoota hi nahi. Isliye ye alag check hai.
#
# PowerShell mein isliye, bash mein nahi: aidl.exe forward-slash wale aur
# spaces wale path se chidta hai, aur bash se sahi Windows path bhejna quoting
# ki ladai ban jaata hai. Yahan file ko temp mein copy kar ke, wahan cd kar ke,
# RELATIVE paths se chalaya jata hai - ye seedha chalta hai.
#
# Chalane ka tarika:  .\tools\check-aidl.ps1

$ErrorActionPreference = 'Continue'

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$src  = Join-Path $here "..\app\src\main\aidl"
if (-not (Test-Path $src)) {
    Write-Host "Koi aidl folder nahi hai - check karne ko kuch nahi." -ForegroundColor DarkGray
    exit 0
}

$buildTools = Get-ChildItem "D:\sdk\Android\Sdk\build-tools" -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name | Select-Object -Last 1
if (-not $buildTools) {
    Write-Host "build-tools nahi mile - AIDL check nahi ho paya." -ForegroundColor Yellow
    exit 0
}
$aidl = Join-Path $buildTools.FullName "aidl.exe"
$framework = "D:\sdk\Android\Sdk\platforms\android-36\framework.aidl"

$work = Join-Path $env:TEMP "hg_aidl_check"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$work\src","$work\out" | Out-Null
Copy-Item "$src\*" "$work\src\" -Recurse -Force

$failed = 0
$base = Join-Path $work "src"

<#
  Relative path Resolve-Path se, Substring se NAHI.

  $env:TEMP is machine par chhota naam deta hai (C:\Users\SAIFUL~1\...) jabki
  FullName poora naam (C:\Users\SAIFULLAH\...). Dono ki lambai alag hai, to
  Substring se kaata gaya path ek-do akshar kha jata tha - "src\c\com\..."
  jaisa kuch banta tha. Resolve-Path ye hisaab khud sambhal leta hai.
#>
Push-Location $base
$relatives = @(Get-ChildItem . -Recurse -Filter *.aidl |
    ForEach-Object { (Resolve-Path $_.FullName -Relative) -replace '^\.\\', '' })
Pop-Location

Push-Location $work
foreach ($relative in $relatives) {
    $output = & $aidl "-p$framework" "-Isrc" "-oout" "src\$relative" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "AIDL FAIL: $relative" -ForegroundColor Red
        $output | ForEach-Object { Write-Host "  $_" }
        $failed++
    }
}
Pop-Location

if ($failed -gt 0) { exit 1 }

$generated = @(Get-ChildItem "$work\out" -Recurse -Filter *.java -ErrorAction SilentlyContinue)
Write-Host ("AIDL OK - {0} file(s) generate hui" -f $generated.Count) -ForegroundColor Green

# Shizuku ki shart: destroy() ko transaction id honi chahiye, warna user
# service kabhi band nahi hogi aur shell process zinda pada rahega.
$destroy = $generated | ForEach-Object { Select-String -Path $_.FullName -Pattern "TRANSACTION_destroy = .*" } | Select-Object -First 1
if ($destroy) {
    Write-Host ("  " + $destroy.Matches[0].Value) -ForegroundColor DarkGray
} else {
    Write-Host "  Dhyan: destroy() ki transaction id nahi mili - Shizuku user service iske bina band nahi hogi." -ForegroundColor Yellow
}
exit 0
