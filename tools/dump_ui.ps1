param([string]$tag = "ui")
$adb = "D:\dev\Android\sdk\platform-tools\adb.exe"
& $adb shell uiautomator dump /sdcard/ui.xml | Out-Null
& $adb pull /sdcard/ui.xml "$PSScriptRoot\..\dist\ui.xml" | Out-Null
[xml]$doc = Get-Content "$PSScriptRoot\..\dist\ui.xml" -Encoding UTF8
$lines = $doc.SelectNodes('//node') | Where-Object { $_.text -ne $null -and $_.text -ne "" } | ForEach-Object { '{0}  |  {1}' -f $_.text, $_.bounds }
$lines | Out-File -Encoding UTF8 "$PSScriptRoot\..\dist\ui_$tag.txt"
Write-Host "dumped to dist\ui_$tag.txt ($($lines.Count) texts)"
