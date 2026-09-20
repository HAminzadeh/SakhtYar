$ErrorActionPreference = "Stop"

# SakhtYar local Ollama launcher for machines where CUDA/GPU execution is unstable
# (for example older NVIDIA drivers or small VRAM GPUs).
# This starts Ollama fully locally on CPU and disables Ollama cloud features.

$env:CUDA_VISIBLE_DEVICES = "-1"
$env:OLLAMA_LLM_LIBRARY = "cpu_avx2"
$env:OLLAMA_NO_CLOUD = "true"

Write-Host "Starting Ollama for SakhtYar in CPU-only/local mode..." -ForegroundColor Green
Write-Host "CUDA_VISIBLE_DEVICES=$env:CUDA_VISIBLE_DEVICES"
Write-Host "OLLAMA_LLM_LIBRARY=$env:OLLAMA_LLM_LIBRARY"
Write-Host "OLLAMA_NO_CLOUD=$env:OLLAMA_NO_CLOUD"
Write-Host "API: http://127.0.0.1:11434"
Write-Host "Press Ctrl+C to stop Ollama."

ollama serve
