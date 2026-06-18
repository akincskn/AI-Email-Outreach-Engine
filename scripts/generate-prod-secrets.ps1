# Production secret generator — AI Email Outreach Engine
# Usage:  .\scripts\generate-prod-secrets.ps1
#
# Generates 4 cryptographically-secure, URL-safe secrets for production.
# Copy them into a password manager, then into Render (backend) + Vercel (frontend).
# This script prints secrets to the console only — it never writes or commits them.

function New-Secret {
    param([int]$Bytes = 36)   # 36 bytes -> 48 base64url chars
    $buffer = New-Object 'System.Byte[]' $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    # base64url: no +, /, = so the value is safe in URLs, env files and headers
    [Convert]::ToBase64String($buffer).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

Write-Host ""
Write-Host "=== PRODUCTION SECRETS ===" -ForegroundColor Cyan
Write-Host "Her birini guvenli bir yere kaydet (password manager)." -ForegroundColor DarkGray
Write-Host ""

Write-Host "API_KEY        (Render + Vercel AYNI olmali):" -ForegroundColor Yellow
New-Secret
Write-Host ""
Write-Host "HMAC_SECRET    (Render):" -ForegroundColor Yellow
New-Secret
Write-Host ""
Write-Host "JWT_SECRET     (Render):" -ForegroundColor Yellow
New-Secret
Write-Host ""
Write-Host "AUTH_SECRET    (Vercel; NEXTAUTH_SECRET ile ayni):" -ForegroundColor Yellow
New-Secret
Write-Host ""
Write-Host "Ek olarak N8N_WEBHOOK_API_KEY ve sifreler de gerekebilir:" -ForegroundColor DarkGray
Write-Host "N8N_WEBHOOK_API_KEY:" -ForegroundColor Yellow
New-Secret
Write-Host ""
Write-Host "UYARI: Bu degerleri repoya veya chat'e yapistirma." -ForegroundColor Red
Write-Host ""
