# stdin: the paste as JSON (same shape as `crosspaste paste --json`).
# stdout: the result, UTF-8. A non-zero exit reports stderr to the user.
# Windows PowerShell 5.1 decodes and encodes the console in the OEM code page,
# so read and write raw UTF-8 bytes instead of using Read-Host / Write-Output.
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding $false
try {
    $reader = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), $utf8)
    $paste = $reader.ReadToEnd() | ConvertFrom-Json
    $text = if ($paste.rawContent) { $paste.rawContent } else { $paste.content }
    $bytes = $utf8.GetBytes($text.ToUpperInvariant())
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
