# Translates Chinese text to English and anything else to Simplified Chinese.
# stdin: the paste as JSON (same shape as `crosspaste paste --json`).
# stdout: the translation, UTF-8. A non-zero exit reports stderr to the user.
# Windows PowerShell 5.1 decodes and encodes the console in the OEM code page,
# so read and write raw UTF-8 bytes instead of using Read-Host / Write-Output.
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding $false
$reader = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), $utf8)
$paste = $reader.ReadToEnd() | ConvertFrom-Json
$text = if ($paste.content) { $paste.content } else { $paste.rawContent }
$target = if ($text -match '\p{IsCJKUnifiedIdeographs}') { 'en' } else { 'zh-CN' }
$url = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=' + $target +
    '&dt=t&q=' + [Uri]::EscapeDataString($text)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$client = New-Object System.Net.WebClient
$client.Encoding = $utf8
$response = $client.DownloadString($url) | ConvertFrom-Json
$translated = ($response[0] | ForEach-Object { $_[0] }) -join ''
$bytes = $utf8.GetBytes($translated)
$stdout = [Console]::OpenStandardOutput()
$stdout.Write($bytes, 0, $bytes.Length)
$stdout.Flush()
