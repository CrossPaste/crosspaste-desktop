# Translates the text into the CrossPaste UI language (CROSSPASTE_LANGUAGE); text already
# in that language goes to English instead.
# stdin: the paste as JSON (same shape as `crosspaste paste --json`).
# stdout: the translation, UTF-8. A non-zero exit reports stderr to the user.
# Windows PowerShell 5.1 decodes and encodes the console in the OEM code page,
# so read and write raw UTF-8 bytes instead of using Read-Host / Write-Output.
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding $false
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Translate-Text([string]$text, [string]$target) {
    $url = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=' + $target +
        '&dt=t&q=' + [Uri]::EscapeDataString($text)
    $client = New-Object System.Net.WebClient
    $client.Encoding = $utf8
    $response = $client.DownloadString($url) | ConvertFrom-Json
    $translated = ($response[0] | ForEach-Object { $_[0] }) -join ''
    $source = if ($response[2]) { [string]$response[2] } else { '' }
    return @{ Text = $translated; Source = $source }
}

$reader = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), $utf8)
$paste = $reader.ReadToEnd() | ConvertFrom-Json
$text = if ($paste.content) { $paste.content } else { $paste.rawContent }
$language = if ($env:CROSSPASTE_LANGUAGE) { $env:CROSSPASTE_LANGUAGE } else { 'en' }
# CrossPaste language codes that differ from Google's
$target = switch ($language) { 'zh' { 'zh-CN' } 'zh_hant' { 'zh-TW' } default { $language } }
$result = Translate-Text $text $target
# Detection is unreliable (Traditional Chinese can come back as "en"), so an
# unchanged result also means the text is already in the target language
$alreadyTarget = ($result.Source.Split('-')[0] -eq $target.Split('-')[0]) -or ($result.Text.Trim() -eq $text.Trim())
if ($alreadyTarget -and $target -ne 'en') {
    $result = Translate-Text $text 'en'
}
$bytes = $utf8.GetBytes($result.Text)
$stdout = [Console]::OpenStandardOutput()
$stdout.Write($bytes, 0, $bytes.Length)
$stdout.Flush()
