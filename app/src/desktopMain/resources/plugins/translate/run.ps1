# Translates the text into the language picked from the submenu: CROSSPASTE_ACTION carries
# the chosen action id, which is a Google language code.
# stdin: the paste as JSON (same shape as `crosspaste paste --json`).
# stdout: the translation, UTF-8. A non-zero exit reports stderr to the user.
# Windows PowerShell 5.1 decodes and encodes the console in the OEM code page,
# so read and write raw UTF-8 bytes instead of using Read-Host / Write-Output.
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding $false
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Translate-Text([string]$text, [string]$target) {
    $url = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=' +
        [Uri]::EscapeDataString($target) + '&dt=t&q=' + [Uri]::EscapeDataString($text)
    $client = New-Object System.Net.WebClient
    $client.Encoding = $utf8
    $response = $client.DownloadString($url) | ConvertFrom-Json
    return ($response[0] | ForEach-Object { $_[0] }) -join ''
}

try {
    $reader = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), $utf8)
    $paste = $reader.ReadToEnd() | ConvertFrom-Json
    $text = if ($paste.content) { $paste.content } else { $paste.rawContent }
    $target = if ($env:CROSSPASTE_ACTION) { $env:CROSSPASTE_ACTION } else { 'en' }
    $bytes = $utf8.GetBytes((Translate-Text $text $target))
    $stdout = [Console]::OpenStandardOutput()
    $stdout.Write($bytes, 0, $bytes.Length)
    $stdout.Flush()
} catch {
    # CrossPaste shows the tail of stderr to the user; one line reads better than an error record
    $errBytes = $utf8.GetBytes($_.Exception.Message + "`n")
    $stderr = [Console]::OpenStandardError()
    $stderr.Write($errBytes, 0, $errBytes.Length)
    $stderr.Flush()
    exit 1
}
