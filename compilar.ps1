# compilar.ps1
# Compila o Audio Roulette (.apk) usando Docker, em vez do WSL.
# Roda inteiramente via linha de comando - nenhuma etapa exige tela visual.

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogFile = Join-Path $ProjectDir "build_log.txt"

Write-Host "============================================================"
Write-Host "  AUDIO ROULETTE - Build via Docker"
Write-Host "============================================================"
Write-Host ""
Write-Host "Este metodo usa Docker em vez do WSL. Evita os problemas de"
Write-Host "senha, DNS e cache misturado que tivemos antes."
Write-Host ""
Write-Host "Tudo que aparecer aqui tambem fica salvo em build_log.txt,"
Write-Host "nesta mesma pasta."
Write-Host "============================================================"
Write-Host ""

# --- Verifica se o Docker esta instalado ---
$dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCmd) {
    Write-Host "[ERRO] Docker nao foi encontrado neste computador." -ForegroundColor Red
    Write-Host ""
    Write-Host "Instale o Docker Desktop (gratuito) em:"
    Write-Host "  https://www.docker.com/products/docker-desktop/"
    Write-Host ""
    Write-Host "Depois de instalar, reinicie o computador e rode este"
    Write-Host "arquivo de novo."
    Read-Host "Pressione ENTER para sair"
    exit 1
}

# --- Verifica se o Docker Desktop esta rodando ---
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] O Docker Desktop nao esta rodando." -ForegroundColor Red
    Write-Host "Abra o Docker Desktop, espere ele iniciar completamente"
    Write-Host "(pode levar um minuto), e rode este arquivo de novo."
    Read-Host "Pressione ENTER para sair"
    exit 1
}

Write-Host "Docker encontrado e rodando. Prosseguindo..."
Write-Host ""

"Build iniciado em $(Get-Date)" | Out-File -FilePath $LogFile -Encoding Unicode

# --- Etapa 1: construir a imagem Docker ---
Write-Host "[1/2] Construindo o ambiente de build (primeira vez pode"
Write-Host "      demorar alguns minutos; nas proximas vezes e quase"
Write-Host "      instantaneo, o Docker guarda em cache)."
Write-Host ""

docker build -t audioroulette-builder $ProjectDir 2>&1 | Tee-Object -FilePath $LogFile -Append

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[ERRO] Falha ao construir o ambiente Docker." -ForegroundColor Red
    Write-Host "Log completo em: $LogFile"
    Read-Host "Pressione ENTER para sair"
    exit 1
}

Write-Host ""
Write-Host "[2/2] Compilando o APK dentro do container..."
Write-Host "Na PRIMEIRA VEZ, o buildozer ainda baixa sozinho o Android"
Write-Host "SDK e NDK (varios GB) - pode demorar 30-60 minutos."
Write-Host "Nas proximas vezes sera bem mais rapido (fica em cache)."
Write-Host ""

# --- Etapa 2: rodar o build de verdade ---
# O cache do buildozer (SDK/NDK/etc.) fica num volume Docker nomeado,
# que persiste entre execucoes sem precisar baixar tudo de novo.
docker run --rm `
    -v "${ProjectDir}:/app" `
    -v "audioroulette_buildozer_cache:/home/builder/.buildozer" `
    -w /app `
    audioroulette-builder `
    buildozer -v android debug 2>&1 | Tee-Object -FilePath $LogFile -Append

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Red
    Write-Host "  [ERRO] O build falhou." -ForegroundColor Red
    Write-Host "  Log completo salvo em: $LogFile"
    Write-Host "  Envie esse arquivo para eu identificar o problema."
    Write-Host "============================================================" -ForegroundColor Red
    Read-Host "Pressione ENTER para sair"
    exit 1
}

Write-Host ""
Write-Host "Build concluido! Verificando o arquivo .apk gerado..."
Write-Host ""

$binDir = Join-Path $ProjectDir "bin"
$apk = Get-ChildItem -Path $binDir -Filter *.apk -ErrorAction SilentlyContinue

if ($apk) {
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  SUCESSO! APK gerado em:"
    Write-Host "  $($apk.FullName)"
    Write-Host "============================================================" -ForegroundColor Green
} else {
    Write-Host "[AVISO] Build terminou mas nenhum .apk foi encontrado em bin\."
    Write-Host "Verifique o log completo em: $LogFile"
}

Write-Host ""
Read-Host "Pressione ENTER para sair"
