param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)]
    [string]$Token,
    [Parameter(Mandatory = $true)]
    [string]$RequestFile,
    [ValidateSet("before", "parallel", "cache-miss", "cache", "after")]
    [string]$Stage = "before",
    [ValidateRange(1, 1000)]
    [int]$Runs = 20,
    [ValidateRange(0, 100)]
    [int]$Warmup = 3,
    [string]$OutputDirectory = $PSScriptRoot
)

$ErrorActionPreference = "Stop"
$metricNames = @(
    "review.total",
    "review.image.pipeline",
    "review.image.single",
    "review.claude.call"
)
$headers = @{
    Cookie = "accessToken=$Token"
    "Content-Type" = "application/json"
}
$resolvedRequestFile = (Resolve-Path -LiteralPath $RequestFile).Path
$requestBody = Get-Content -LiteralPath $resolvedRequestFile -Raw
$requestSha256 = (Get-FileHash -LiteralPath $resolvedRequestFile -Algorithm SHA256).Hash.ToLowerInvariant()
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$gitCommit = (& git -C $repositoryRoot rev-parse HEAD 2>$null)
$gitDirty = [bool](& git -C $repositoryRoot status --porcelain 2>$null)

function Get-MetricSnapshot {
    $snapshot = @{}
    foreach ($metricName in $metricNames) {
        $count = 0.0
        $totalSeconds = 0.0
        try {
            $encodedName = [Uri]::EscapeDataString($metricName)
            $response = Invoke-RestMethod `
                -Uri "$BaseUrl/actuator/metrics/$encodedName" `
                -Method Get `
                -Headers $headers
            foreach ($measurement in $response.measurements) {
                if ($measurement.statistic -eq "COUNT") {
                    $count = [double]$measurement.value
                }
                if ($measurement.statistic -eq "TOTAL_TIME") {
                    $totalSeconds = [double]$measurement.value
                }
            }
        } catch {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404) {
                # The meter does not exist until its first invocation.
            } else {
                throw
            }
        }
        $snapshot[$metricName] = @{
            count = $count
            totalSeconds = $totalSeconds
        }
    }
    return $snapshot
}

function Invoke-ReviewRequest {
    $before = Get-MetricSnapshot
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $statusCode = 0
    $errorMessage = ""
    try {
        $response = Invoke-WebRequest `
            -Uri "$BaseUrl/detail-pages/review-copy" `
            -Method Post `
            -Headers $headers `
            -Body $requestBody `
            -UseBasicParsing
        $statusCode = [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        $errorMessage = $_.Exception.Message
    } finally {
        $stopwatch.Stop()
    }
    $after = Get-MetricSnapshot

    return [pscustomobject]@{
        measured_at = (Get-Date).ToString("o")
        stage = $Stage
        git_commit = $gitCommit
        git_dirty = $gitDirty
        request_sha256 = $requestSha256
        base_url = $BaseUrl
        status_code = $statusCode
        client_total_ms = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 3)
        server_total_ms = [math]::Round(
            ($after["review.total"].totalSeconds - $before["review.total"].totalSeconds) * 1000,
            3
        )
        image_pipeline_ms = [math]::Round(
            ($after["review.image.pipeline"].totalSeconds - $before["review.image.pipeline"].totalSeconds) * 1000,
            3
        )
        image_single_count = [math]::Round(
            $after["review.image.single"].count - $before["review.image.single"].count,
            0
        )
        image_single_total_ms = [math]::Round(
            ($after["review.image.single"].totalSeconds - $before["review.image.single"].totalSeconds) * 1000,
            3
        )
        claude_call_ms = [math]::Round(
            ($after["review.claude.call"].totalSeconds - $before["review.claude.call"].totalSeconds) * 1000,
            3
        )
        error = $errorMessage
    }
}

function Get-Percentile([double[]]$Values, [double]$Percentile) {
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return 0.0 }
    $index = [math]::Ceiling($Percentile * $sorted.Count) - 1
    return [double]$sorted[[math]::Max(0, $index)]
}

Write-Host "Warming up with $Warmup request(s)..."
for ($index = 1; $index -le $Warmup; $index++) {
    $warmupResult = Invoke-ReviewRequest
    Write-Host "  warmup $index/$Warmup - HTTP $($warmupResult.status_code), $($warmupResult.client_total_ms) ms"
}

$results = @()
Write-Host "Measuring $Runs request(s)..."
for ($index = 1; $index -le $Runs; $index++) {
    $result = Invoke-ReviewRequest
    $results += $result
    Write-Host "  run $index/$Runs - HTTP $($result.status_code), $($result.client_total_ms) ms"
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$rawPath = Join-Path $OutputDirectory "$Stage-$timestamp.csv"
$summaryPath = Join-Path $OutputDirectory "$Stage-$timestamp-summary.csv"
$results | Export-Csv -LiteralPath $rawPath -NoTypeInformation -Encoding UTF8

$successfulResults = @($results | Where-Object { $_.status_code -eq 200 })
$columns = @("client_total_ms", "server_total_ms", "image_pipeline_ms", "image_single_total_ms", "claude_call_ms")
$summary = foreach ($column in $columns) {
    [double[]]$values = @($successfulResults | ForEach-Object { [double]$_.$column })
    [pscustomobject]@{
        stage = $Stage
        metric = $column
        samples = $values.Count
        mean_ms = if ($values.Count) { [math]::Round(($values | Measure-Object -Average).Average, 3) } else { 0 }
        p50_ms = [math]::Round((Get-Percentile $values 0.50), 3)
        p95_ms = [math]::Round((Get-Percentile $values 0.95), 3)
        min_ms = if ($values.Count) { [math]::Round(($values | Measure-Object -Minimum).Minimum, 3) } else { 0 }
        max_ms = if ($values.Count) { [math]::Round(($values | Measure-Object -Maximum).Maximum, 3) } else { 0 }
    }
}
$summary | Export-Csv -LiteralPath $summaryPath -NoTypeInformation -Encoding UTF8

Write-Host "Raw results: $rawPath"
Write-Host "Summary:     $summaryPath"
if ($successfulResults.Count -ne $Runs) {
    Write-Warning "$($Runs - $successfulResults.Count) request(s) failed. Check the raw CSV before using the summary."
}
