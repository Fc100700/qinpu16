$ErrorActionPreference = "Stop"
$p = "C:\Users\13224\opencode_project\qinpu16\dist\qinpu_backup.xml"
$t = [IO.File]::ReadAllText($p, [Text.Encoding]::UTF8)

# 1) restore first set (jie-bie-shu) x/y/scale to original values
$t = [Regex]::Replace($t, '(\[\{&quot;name&quot;:&quot;[^&]*&quot;,&quot;uris&quot;:\[[^\]]*\],&quot;x&quot;:)\d+(,&quot;y&quot;:)\d+(,&quot;scale&quot;:)[0-9.]+', '${1}1002${2}182${3}0.8773153424263')

# 2) restore globals to "cry for me" state
$t = [Regex]::Replace($t, '<int name="x" value="\d+" />', '<int name="x" value="935" />')
$t = [Regex]::Replace($t, '<int name="y" value="\d+" />', '<int name="y" value="168" />')
$t = [Regex]::Replace($t, '<float name="scale" value="[0-9.]+" />', '<float name="scale" value="0.72" />')
$t = [Regex]::Replace($t, '<string name="currentSet">[^<]*</string>', '<string name="currentSet">cry for me</string>')
$u = 'content://com.android.providers.media.documents/document/image%3A4593&#10;content://com.android.providers.media.documents/document/image%3A4594&#10;content://com.android.providers.media.documents/document/image%3A4595&#10;content://com.android.providers.media.documents/document/image%3A4596&#10;content://com.android.providers.media.documents/document/image%3A4597'
$t = [Regex]::Replace($t, '<string name="uris">[^<]*</string>', '<string name="uris">' + $u + '</string>')

[IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "restored xml written"
