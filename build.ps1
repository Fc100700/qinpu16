param(
    [switch]$SkipSign
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = "D:\dev\Android\sdk"
$bt = "$sdk\build-tools\36.1.0"
$androidJar = "$sdk\platforms\android-36\android.jar"
$jdk = "D:\dev\jdk"
$javac = "$jdk\bin\javac.exe"
$keytool = "$jdk\bin\keytool.exe"
$d8 = "$bt\d8.bat"
$build = "$root\build"
$dist = "$root\dist"

if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force -Path "$build\gen","$build\classes","$build\dex","$dist" | Out-Null

Write-Host "[1/6] aapt2 compile resources"
& "$bt\aapt2.exe" compile --dir "$root\res" -o "$build\compiled.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/6] aapt2 link"
& "$bt\aapt2.exe" link -o "$build\base.apk" -I $androidJar --manifest "$root\AndroidManifest.xml" --java "$build\gen" --min-sdk-version 26 --target-sdk-version 34 "$build\compiled.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/6] javac"
$sources = @()
$sources += Get-ChildItem -Recurse -Filter *.java "$root\src" | ForEach-Object { $_.FullName }
$sources += Get-ChildItem -Recurse -Filter *.java "$build\gen" | ForEach-Object { $_.FullName }
& $javac "-J-Duser.language=en" "-J-Duser.country=US" -encoding UTF-8 --release 8 -nowarn -classpath $androidJar -d "$build\classes" @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/6] d8"
$classes = Get-ChildItem -Recurse -Filter *.class "$build\classes" | ForEach-Object { $_.FullName }
& $d8 --release --lib $androidJar --min-api 26 --output "$build\dex" @classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/6] package + zipalign"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$unsigned = "$build\unsigned.apk"
Copy-Item "$build\base.apk" $unsigned
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, 'Update')
$old = $zip.GetEntry("classes.dex")
if ($old) { $old.Delete() }
$entry = $zip.CreateEntry("classes.dex", [System.IO.Compression.CompressionLevel]::Optimal)
$stream = $entry.Open()
$bytes = [IO.File]::ReadAllBytes("$build\dex\classes.dex")
$stream.Write($bytes, 0, $bytes.Length)
$stream.Close()
$zip.Dispose()
& "$bt\zipalign.exe" -f -p 4 $unsigned "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

if ($SkipSign) {
    Copy-Item "$build\aligned.apk" "$dist\qinpu-1.8-unsigned-aligned.apk" -Force
    Write-Host "Unsigned aligned APK: $dist\qinpu-1.8-unsigned-aligned.apk"
    exit 0
}

Write-Host "[6/6] sign"
$ks = "$root\keystore\qinpu-release.jks"
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force -Path "$root\keystore" | Out-Null
    & $keytool -genkeypair -keystore $ks -alias qinpu -keyalg RSA -keysize 2048 -validity 10950 -storepass qinpu123456 -keypass qinpu123456 -dname "CN=Qinpu Overlay, O=Qinpu, C=CN" -v
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}
& "$bt\apksigner.bat" sign --ks $ks --ks-key-alias qinpu --ks-pass pass:qinpu123456 --key-pass pass:qinpu123456 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "$dist\qinpu-1.8-release.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }

& "$bt\apksigner.bat" verify --print-certs "$dist\qinpu-1.8-release.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

Copy-Item "$build\aligned.apk" "$dist\qinpu-1.8-unsigned-aligned.apk" -Force
Write-Host "Done: $dist\qinpu-1.8-release.apk"

