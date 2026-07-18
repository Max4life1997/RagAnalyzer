<#
.SYNOPSIS
  Автоматическое развёртывание и запуск RagAnalyzer.

.DESCRIPTION
  Скрипт проверяет и при необходимости устанавливает недостающие зависимости
  (Ollama, модели, Docker-контейнер PostgreSQL/pgvector), собирает проект
  и запускает Spring Boot приложение.

  Требуется предварительно установленные вручную: Java 21 JDK, Docker Desktop.
  Их автоматическая установка не выполняется, так как обычно требует
  перезагрузки/лицензионного соглашения.

.USAGE
  powershell -ExecutionPolicy Bypass -File .\deploy.ps1
#>

$ErrorActionPreference = "Stop"

$ProjectRoot   = $PSScriptRoot
$OllamaUrl     = "https://ollama.com/download/OllamaSetup.exe"
$EmbedModel    = "nomic-embed-text:latest"
$ChatModel     = "qwen2.5:7b"
$AppUrl        = "http://localhost:8080"
$RepoUrl       = "https://github.com/Max4life1997/RagAnalyzer.git"

function Write-Step($msg) {
    Write-Host ""
    Write-Host ">> $msg" -ForegroundColor Cyan
}

function Test-Command($name) {
    return [bool](Get-Command $name -ErrorAction SilentlyContinue)
}

Set-Location $ProjectRoot

# 1. Java
Write-Step "Проверка Java 21 JDK"
if (-not (Test-Command "java")) {
    Write-Host "Java не найдена. Установи Java 21 JDK вручную: https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Red
    exit 1
}
$javaVersion = (& java -version 2>&1) -join " "
Write-Host $javaVersion

# 2. Docker
Write-Step "Проверка Docker"
if (-not (Test-Command "docker")) {
    Write-Host "Docker не найден. Установи Docker Desktop вручную: https://www.docker.com/products/docker-desktop/" -ForegroundColor Red
    exit 1
}

# 3. Ollama
Write-Step "Проверка Ollama"
if (-not (Test-Command "ollama")) {
    Write-Host "Ollama не найдена, скачиваю установщик..." -ForegroundColor Yellow
    $installer = Join-Path $env:TEMP "OllamaSetup.exe"
    Invoke-WebRequest -Uri $OllamaUrl -OutFile $installer
    Write-Host "Запускаю установку Ollama (следуй мастеру установки)..."
    Start-Process -FilePath $installer -Wait
    Remove-Item $installer -Force -ErrorAction SilentlyContinue

    if (-not (Test-Command "ollama")) {
        Write-Host "Ollama всё ещё не найдена в PATH. Открой новый терминал и запусти скрипт заново." -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "Ollama уже установлена."
}

# 4. Модели Ollama
Write-Step "Проверка/скачивание моделей Ollama"
$installedModels = (& ollama list) -join "`n"

foreach ($model in @($EmbedModel, $ChatModel)) {
    if ($installedModels -notmatch [regex]::Escape($model)) {
        Write-Host "Скачиваю модель $model ..." -ForegroundColor Yellow
        & ollama pull $model
    } else {
        Write-Host "Модель $model уже скачана."
    }
}

# 5. PostgreSQL (pgvector) через Docker Compose
Write-Step "Запуск PostgreSQL (pgvector) через Docker Compose"
& docker compose up -d postgres

# 6. Сборка и запуск приложения
Write-Step "Сборка и запуск RagAnalyzer (mvn spring-boot:run)"
Write-Host "Приложение поднимется на $AppUrl" -ForegroundColor Green
Write-Host "Репозиторий проекта: $RepoUrl"
Write-Host ""

& mvn spring-boot:run
