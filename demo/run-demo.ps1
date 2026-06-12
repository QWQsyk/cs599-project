$ErrorActionPreference = "Stop"
Set-Location "E:\Data\PythonLearnFile\LawAgent"

$backend = [System.Diagnostics.Process]::new()
$backend.StartInfo.FileName = "E:\Develop\NodeJs\node.exe"
$backend.StartInfo.Arguments = "demo/backend/server.mjs"
$backend.StartInfo.WorkingDirectory = "E:\Data\PythonLearnFile\LawAgent"
$backend.StartInfo.UseShellExecute = $false
$backend.StartInfo.RedirectStandardOutput = $false
$backend.StartInfo.RedirectStandardError = $false
$backend.StartInfo.CreateNoWindow = $true
$backend.Start() | Out-Null

$frontend = [System.Diagnostics.Process]::new()
$frontend.StartInfo.FileName = "E:\Develop\NodeJs\node.exe"
$frontend.StartInfo.Arguments = "demo/frontend/server.mjs"
$frontend.StartInfo.WorkingDirectory = "E:\Data\PythonLearnFile\LawAgent"
$frontend.StartInfo.UseShellExecute = $false
$frontend.StartInfo.RedirectStandardOutput = $false
$frontend.StartInfo.RedirectStandardError = $false
$frontend.StartInfo.CreateNoWindow = $true
$frontend.Start() | Out-Null

Write-Host "Demo backend/frontend processes started."
Write-Host "Backend pid: $($backend.Id), frontend pid: $($frontend.Id)"
Write-Host "LLM provider: $($env:LLM_PROVIDER)"
Write-Host "LLM model: $($env:LLM_MODEL)"
Write-Host "LLM key configured: $([bool]$env:LLM_API_KEY)"
Write-Host "Open http://localhost:5173"

try {
  while ($true) {
    Start-Sleep -Seconds 5
    if ($backend.HasExited) {
      Write-Host "Backend exited:" -ForegroundColor Red
      break
    }
    if ($frontend.HasExited) {
      Write-Host "Frontend exited:" -ForegroundColor Red
      break
    }
  }
} finally {
  if (-not $backend.HasExited) { $backend.Kill() }
  if (-not $frontend.HasExited) { $frontend.Kill() }
}
