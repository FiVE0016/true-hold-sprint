$ErrorActionPreference = 'Stop'
# 手工构建：不依赖 Gradle / 不联网
# 26.1 起 Minecraft 不再混淆，mod 不需要 remap，javac + jar 就够了。
# 注意：javac 的 @argfile 里反斜杠会被当成转义符，所以 classpath 里的路径要换成正斜杠！
$cache = Join-Path $env:USERPROFILE '.gradle\caches'
$mc = Join-Path $cache 'fabric-loom\26.3\minecraft-merged.jar'
if (-not (Test-Path $mc)) { throw "Minecraft jar not found: $mc" }

$libRoot = Join-Path $cache 'modules-2\files-2.1'
$all = @(Get-ChildItem -LiteralPath $libRoot -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Extension -eq '.jar' -and $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' })
Write-Output ("library jars found: " + $all.Count)

$entries = @($mc) + @($all | ForEach-Object { $_.FullName })
$cp = ($entries | ForEach-Object { $_ -replace '\\', '/' }) -join ';'

$out = Join-Path (Get-Location) 'build\manual'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null

$sources = @(Get-ChildItem 'src\main\java' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$argsFile = Join-Path (Get-Location) 'build\javac-args.txt'
Set-Content -Path $argsFile -Value ('-cp "' + $cp + '"') -Encoding ASCII

Write-Output ("compiling " + $sources.Count + " java files ...")
& javac -proc:none --release 25 -encoding UTF-8 -nowarn -d $out "@$argsFile" $sources
if ($LASTEXITCODE -ne 0) { Write-Output 'COMPILE FAILED'; exit 1 }
Write-Output 'COMPILE OK'

$version = (Select-String -Path 'gradle.properties' -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value.Trim()
Copy-Item 'src\main\resources\*' $out -Recurse -Force
$fmj = Join-Path $out 'fabric.mod.json'
(Get-Content $fmj -Raw -Encoding UTF8).Replace('$' + '{version}', $version) | Set-Content $fmj -Encoding UTF8 -NoNewline

$libs = Join-Path (Get-Location) 'build\libs'
New-Item -ItemType Directory -Force -Path $libs | Out-Null
$jarPath = Join-Path $libs ("true-hold-sprint-" + $version + "-manual.jar")
if (Test-Path $jarPath) { Remove-Item $jarPath -Force }
& jar --create --file $jarPath -C $out .
if ($LASTEXITCODE -ne 0) { Write-Output 'JAR FAILED'; exit 1 }
Write-Output ('JAR OK -> ' + $jarPath + '  (' + [math]::Round((Get-Item $jarPath).Length/1KB,1) + ' KB)')
