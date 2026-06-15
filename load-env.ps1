# load-env.ps1 — .env.local'ı PowerShell process env'ine yükler
# Kullanım:  . .\load-env.ps1   (dot-source ile çağır)
$envFile = Join-Path $PSScriptRoot ".env.local"
if (-not (Test-Path $envFile)) {
    Write-Host "HATA: .env.local bulunamadi: $envFile" -ForegroundColor Red
    return
}
$loaded = 0
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^([^=#]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable(
            $matches[1].Trim(), $matches[2].Trim(), "Process")
        $loaded++
    }
}
Write-Host "Env loaded ($loaded vars)" -ForegroundColor Green
