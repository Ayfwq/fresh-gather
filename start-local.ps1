param(
    [switch]$FrontendOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontendRoot = Join-Path $projectRoot 'frontend'
$serverRoot = Join-Path $projectRoot 'backend\sky-server'
$runtimeRoot = Join-Path $projectRoot '.runtime'

function Test-TcpEndpoint([string]$HostName, [int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(750)) { return $false }
        $client.EndConnect($pending)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw '需要先安装 Node.js（附带 npm），然后从项目根目录重新运行 ./start-local.ps1。'
}
if (-not (Test-Path (Join-Path $frontendRoot 'node_modules'))) {
    Write-Host '正在准备前端依赖…'
    & npm --prefix $frontendRoot install
    if ($LASTEXITCODE -ne 0) { throw '安装前端依赖失败。' }
}

New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
if (-not $FrontendOnly) {
    $javaExecutable = $null
    $javaCandidates = @()
    if ($env:JAVA_HOME) { $javaCandidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
    $javaCandidates += Get-Command java.exe -All -ErrorAction SilentlyContinue | ForEach-Object { $_.Source }
    foreach ($javaCandidate in ($javaCandidates | Where-Object { $_ } | Select-Object -Unique)) {
        if (-not (Test-Path $javaCandidate)) { continue }
        & $javaCandidate -version *> $null
        if ($LASTEXITCODE -eq 0) { $javaExecutable = $javaCandidate; break }
    }

    $redisHost = if ($env:SPRING_REDIS_HOST) { $env:SPRING_REDIS_HOST } else { 'localhost' }
    $redisPort = if ($env:SPRING_REDIS_PORT) { [int]$env:SPRING_REDIS_PORT } else { 6379 }
    $redisReady = Test-TcpEndpoint $redisHost $redisPort
    if (-not $redisReady -and $redisHost -in @('localhost', '127.0.0.1', '::1') -and (Get-Command docker -ErrorAction SilentlyContinue)) {
        $null = & docker info --format '{{.ServerVersion}}' 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host '正在启动本地 Redis（Docker Compose）…'
            & docker compose -f (Join-Path $projectRoot 'docker-compose.yml') up -d redis
            if ($LASTEXITCODE -eq 0) {
                for ($attempt = 0; $attempt -lt 30; $attempt++) {
                    if (Test-TcpEndpoint '127.0.0.1' 6379) { $redisReady = $true; break }
                    Start-Sleep -Seconds 1
                }
            }
        }
    }
    if ($redisReady) {
        Write-Host 'Redis 可连接；Java 后端将使用本地缓存和限时特卖库存。'
    } else {
        Write-Warning 'Redis 当前不可连接；真实后端不会启动。请启动 Docker Desktop 或本机 Redis 后重新运行脚本。'
    }

    $rabbitHost = if ($env:SPRING_RABBITMQ_HOST) { $env:SPRING_RABBITMQ_HOST } else { 'localhost' }
    $rabbitPort = if ($env:SPRING_RABBITMQ_PORT) { [int]$env:SPRING_RABBITMQ_PORT } else { 5672 }
    $rabbitReady = Test-TcpEndpoint $rabbitHost $rabbitPort
    if (-not $rabbitReady -and $rabbitHost -in @('localhost', '127.0.0.1', '::1') -and (Get-Command docker -ErrorAction SilentlyContinue)) {
        $null = & docker info --format '{{.ServerVersion}}' 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host '正在启动本地 RabbitMQ（Docker Compose）…'
            & docker compose -f (Join-Path $projectRoot 'docker-compose.yml') up -d rabbitmq
            if ($LASTEXITCODE -eq 0) {
                for ($attempt = 0; $attempt -lt 30; $attempt++) {
                    if (Test-TcpEndpoint '127.0.0.1' 5672) { $rabbitReady = $true; break }
                    Start-Sleep -Seconds 1
                }
            }
        }
    }
    if ($rabbitReady) {
        Write-Host 'RabbitMQ 可连接；新启动的 Java 后端会启用异步抢购和超时补偿。'
    } else {
        Write-Warning 'RabbitMQ 当前不可连接；后端会明确以同步建单模式运行。要启用 RabbitMQ 异步抢购，请启动 Docker Desktop 后重新运行脚本，或配置可连接的 RabbitMQ。'
    }

    $serverReady = $false
    $backendInUse = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($backendInUse) {
        try {
            $probe = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/user/shop/status' -TimeoutSec 3
            $serverReady = $probe.StatusCode -eq 200
            if ($serverReady) {
                Write-Host '复用已运行的 Java 后端（127.0.0.1:8080）。'
                if ($rabbitReady) { Write-Warning '现有后端不会自动切换 RabbitMQ 配置；请重启该后端后，异步抢购才会生效。' }
            }
        } catch {
            Write-Warning '端口 8080 已被占用，但没有检测到可用的本地后端；为避免影响其他程序，将继续启动前端演示。'
        }
    } else {
        if (-not $redisReady) {
            Write-Warning '尚未启动 Java 后端；启动 Redis 后重新运行本脚本即可连接真实接口。'
        } elseif (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
            Write-Warning '未检测到 Maven；将只启动前端演示模式。'
        } elseif (-not $javaExecutable) {
            Write-Warning '未找到可运行的 Java 运行环境；将只启动前端演示模式。'
        } else {
            Write-Host '正在构建 Java 后端（首次构建通常需要 1 分钟左右）…'
            & mvn -f (Join-Path $projectRoot 'backend\pom.xml') -pl sky-server -am -DskipTests package
            if ($LASTEXITCODE -ne 0) { throw 'Java 后端构建失败；请检查 Maven 输出和 .runtime/backend-error.log。' }
            $backendJar = Join-Path $serverRoot 'target\sky-server-1.0-SNAPSHOT.jar'
            if (-not (Test-Path $backendJar)) { throw "后端程序包不存在：$backendJar" }
            Write-Host '正在启动本地后端（127.0.0.1:8080）…'
            $asyncArgument = '--sky.flash-sale.async-enabled=' + $rabbitReady.ToString().ToLowerInvariant()
            Start-Process -FilePath $javaExecutable -ArgumentList @('-jar', $backendJar, '--spring.profiles.active=dev,webdev', $asyncArgument) -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtimeRoot 'backend.log') -RedirectStandardError (Join-Path $runtimeRoot 'backend-error.log') | Out-Null
            for ($attempt = 0; $attempt -lt 40; $attempt++) {
                Start-Sleep -Seconds 2
                $listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($listener) { $serverReady = $true; break }
                if (Test-Path (Join-Path $runtimeRoot 'backend-error.log')) {
                    $log = Get-Content (Join-Path $runtimeRoot 'backend-error.log') -Raw -ErrorAction SilentlyContinue
                    if ($log -match 'APPLICATION FAILED TO START|Application run failed|Communications link failure|Unable to connect to Redis') {
                        Write-Warning '后端启动失败。可以先使用演示模式预览前端，并检查本机 MySQL / Redis 和 .runtime/backend-error.log。'
                        break
                    }
                }
            }
            if ($serverReady) { Write-Host 'Java 后端已启动。' } else { Write-Warning '等待后端启动超时；顾客登录和真实交易暂不可用，可在商家工作台切换演示数据预览。' }
        }
    }

    if ($serverReady) {
        $agentRoot = Join-Path $projectRoot 'backend\sky-agent-py'
        $agentPortInUse = Get-NetTCPConnection -State Listen -LocalPort 8001 -ErrorAction SilentlyContinue | Select-Object -First 1
        $uvCommand = Get-Command uv -ErrorAction SilentlyContinue
        if ($agentPortInUse) {
            Write-Host '经营参谋服务已在 127.0.0.1:8001 运行。'
        } elseif ($uvCommand) {
            Write-Host '正在启动经营参谋服务（127.0.0.1:8001）…'
            $previousSkyServerBaseUrl = $env:SKY_SERVER_BASE_URL
            $env:SKY_SERVER_BASE_URL = 'http://127.0.0.1:8080'
            Start-Process -FilePath $uvCommand.Source -ArgumentList @('run', '--project', $agentRoot, 'python', '-m', 'uvicorn', 'app.main:app', '--app-dir', $agentRoot, '--host', '127.0.0.1', '--port', '8001') -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtimeRoot 'agent.log') -RedirectStandardError (Join-Path $runtimeRoot 'agent-error.log') | Out-Null
            if ($null -eq $previousSkyServerBaseUrl) { Remove-Item Env:\SKY_SERVER_BASE_URL -ErrorAction SilentlyContinue } else { $env:SKY_SERVER_BASE_URL = $previousSkyServerBaseUrl }
            $agentReady = $false
            for ($attempt = 0; $attempt -lt 10; $attempt++) {
                Start-Sleep -Seconds 1
                $listener = Get-NetTCPConnection -State Listen -LocalPort 8001 -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($listener) { $agentReady = $true; break }
            }
            if ($agentReady) { Write-Host '经营参谋服务已启动。' } else { Write-Warning '经营参谋服务未能启动；商品、订单和报表管理不受影响，可检查 .runtime/agent-error.log。' }
        } else {
            Write-Host '未检测到 uv。经营参谋需要 Python 3.11+ 和 uv；其余前后端模块仍可正常运行。'
        }
    }
}

Write-Host ''
Write-Host '鲜集前端正在准备……'
Write-Host '顾客客户端：http://localhost:5173/#market（手机号登录 / 注册后进入商城）'
Write-Host '商家工作台：http://localhost:5173/#merchant（员工账号登录）'
Write-Host '商家演示数据：可在商家工作台中切换'
Write-Host '经营参谋：如果安装了 uv，启动脚本会自动连接到后端分析接口'
Write-Host ''
Write-Host '按 Ctrl+C 结束前端服务。后端在停止前台脚本后继续运行，请在需要时单独关闭它。'
Push-Location $frontendRoot
try {
    & npm run dev
    if ($LASTEXITCODE -ne 0) { throw 'Vite 开发服务器已退出。' }
} finally {
    Pop-Location
}
