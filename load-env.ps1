# load-env.ps1 — .env.local'ı PowerShell process env'ine yükler
# Kullanım:  . .\load-env.ps1   (dot-source ile çağır)
$envFile = Join-Path $PSScriptRoot ".env.local"
if (-not (Test-Path $envFile)) {
    Write-Host "HATA: .env.local bulunamadi: $envFile" -ForegroundColor Red
    return
}
$loaded = 0
# .env.local UTF-8 (BOM'suz) kaydedildi; PowerShell 5.1'in default'u sistem ANSI
# codepage'i (TR locale'de Windows-1254) olduğundan Türkçe karakterler (Türkiye,
# Akın Coşkun) bozulur. UTF-8 okuma ZORUNLU.
Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^([^=#]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable(
            $matches[1].Trim(), $matches[2].Trim(), "Process")
        $loaded++
    }
}
Write-Host "Env loaded ($loaded vars)" -ForegroundColor Green
