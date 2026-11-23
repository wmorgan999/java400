# Finds the newest installed JDK (one with javac) and prints "major|path".
# Set JAVA400_JDK to a JDK folder to force a specific one.
param([int]$MinMajor = 8)

function Get-JdkMajor([string]$jdkDir) {
    $rel = Join-Path $jdkDir 'release'
    if (-not (Test-Path (Join-Path $jdkDir 'bin\javac.exe'))) { return $null }
    $ver = $null
    if (Test-Path $rel) {
        $line = Select-String -Path $rel -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
        if ($line) { $ver = $line.Matches[0].Groups[1].Value }
    }
    if (-not $ver) { return $null }
    $parts = $ver.Split('.')
    if ($parts[0] -eq '1') { return [int]$parts[1] }   # 1.8.0_x -> 8
    return [int]($parts[0] -replace '\D.*$', '')
}

if ($env:JAVA400_JDK) {
    $jdkDir = $env:JAVA400_JDK.TrimEnd('\')
    $m = Get-JdkMajor $jdkDir
    if ($m) { "$m|$jdkDir"; exit 0 }
    [Console]::Error.WriteLine("JAVA400_JDK=$jdkDir is not a usable JDK")
    exit 1
}

$homes = New-Object System.Collections.Generic.List[string]
$roots = 'Java','Eclipse Adoptium','Microsoft','OpenLogic','Zulu','Amazon Corretto','BellSoft','Semeru','IBM'
foreach ($pf in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
    if (-not $pf) { continue }
    foreach ($r in $roots) {
        $dir = Join-Path $pf $r
        if (Test-Path $dir) { Get-ChildItem $dir -Directory | ForEach-Object { $homes.Add($_.FullName) } }
    }
}
if ($env:JAVA_HOME) { $homes.Add($env:JAVA_HOME.TrimEnd('\')) }
Get-Command javac.exe -All -ErrorAction SilentlyContinue | ForEach-Object {
    $homes.Add((Split-Path (Split-Path $_.Source -Parent) -Parent))
}

$best = $homes | Select-Object -Unique | ForEach-Object {
    $m = Get-JdkMajor $_
    if ($m -and $m -ge $MinMajor) { [pscustomobject]@{ Major = $m; Home = $_ } }
} | Sort-Object Major, Home -Descending | Select-Object -First 1

if (-not $best) { [Console]::Error.WriteLine("No JDK $MinMajor or newer found"); exit 1 }
"$($best.Major)|$($best.Home)"
