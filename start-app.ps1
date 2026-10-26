$ErrorActionPreference = 'Stop'

$ports = @(8082, 4200, 65041)
foreach ($port in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conn) {
        $procIds = $conn | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $procIds) {
            try {
                Stop-Process -Id $procId -Force -ErrorAction Stop
                Write-Host "Stopped PID $procId on port $port"
            }
            catch {
                Write-Host "Could not stop PID $procId on port $port"
            }
        }
    }
}

$backendCommand = "Set-Location 'C:/Projects/bitpro/bitpro-transaction-dispute'; $env:SPRING_PROFILES_ACTIVE='dev'; mvn spring-boot:run > backend.log 2>&1"
Start-Process powershell.exe -ArgumentList @('-NoLogo','-NoProfile','-Command',$backendCommand) | Out-Null

$frontendCommand = "Set-Location 'C:/Projects/bitpro/bitpro-transaction-dispute/frontend'; npm start -- --host 0.0.0.0 --port 4200 > frontend.log 2>&1"
Start-Process powershell.exe -ArgumentList @('-NoLogo','-NoProfile','-Command',$frontendCommand) | Out-Null

Start-Sleep -Seconds 20

Write-Host '--- backend health ---'
try {
    $health = Invoke-WebRequest -Uri 'http://localhost:8082/actuator/health' -Method Get -UseBasicParsing
    $health.Content
}
catch {
    Write-Host "Backend health check failed: $($_.Exception.Message)"
}

Write-Host '--- frontend home ---'
try {
    $home = Invoke-WebRequest -Uri 'http://localhost:4200' -Method Get -UseBasicParsing
    $home.Content.Substring(0, [Math]::Min(200, $home.Content.Length))
}
catch {
    Write-Host "Frontend check failed: $($_.Exception.Message)"
}
