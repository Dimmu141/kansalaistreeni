$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$files = Get-ChildItem (Join-Path $workspace 'app/src') -Recurse -File |
    Where-Object { $_.Extension -in '.kt', '.xml', '.json' }
$files += Get-ChildItem $PSScriptRoot -File | Where-Object { $_.Extension -in '.py', '.ps1' }
$files += Get-Item (Join-Path $workspace 'docs/source-review.json')
$failures = @()
foreach ($file in $files) {
    $text = [System.IO.File]::ReadAllText($file.FullName)
    if ($text -match '(?m)[ \t]+\r?$') { $failures += "Trailing whitespace: $($file.FullName)" }
    if ($text.Contains("`t")) { $failures += "Tab indentation: $($file.FullName)" }
    if (-not $text.EndsWith("`n")) { $failures += "Missing final newline: $($file.FullName)" }
    if ($file.Extension -eq '.json') { $null = ConvertFrom-Json -InputObject $text }
    if ($file.Extension -eq '.xml') { $null = [xml]$text }
}
if ($failures.Count -gt 0) { throw ($failures -join "`n") }
[xml]$finnish = Get-Content (Join-Path $workspace 'app/src/main/res/values/strings.xml')
[xml]$english = Get-Content (Join-Path $workspace 'app/src/main/res/values-en/strings.xml')
foreach ($resources in @($finnish, $english)) {
    $keys = @($resources.resources.ChildNodes | ForEach-Object { $_.GetAttribute('name') })
    if (($keys | Select-Object -Unique).Count -ne $keys.Count) { throw 'Duplicate resource key' }
}
# Resources marked translatable="false" (such as the FI/SV switch labels) live only in the base file.
$translatable = @($finnish.resources.ChildNodes | Where-Object { $_.GetAttribute('translatable') -ne 'false' })
$baseKeys = @($translatable | ForEach-Object { $_.GetAttribute('name') })
$translatedKeys = @($english.resources.ChildNodes | ForEach-Object { $_.GetAttribute('name') })
if (Compare-Object $baseKeys $translatedKeys) { throw 'Finnish/English resource keys differ' }
foreach ($resource in $translatable) {
    $translated = $english.resources.ChildNodes | Where-Object { $_.GetAttribute('name') -eq $resource.GetAttribute('name') }
    $baseFormats = @([regex]::Matches($resource.InnerText, '%(?:\d+\$)?[ds]') | ForEach-Object Value)
    $translatedFormats = @([regex]::Matches($translated.InnerText, '%(?:\d+\$)?[ds]') | ForEach-Object Value)
    if (Compare-Object $baseFormats $translatedFormats) { throw "Format arguments differ for $($resource.GetAttribute('name'))" }
    if ($resource.Name -eq 'plurals' -and (Compare-Object $resource.item.quantity $translated.item.quantity)) { throw 'Plural quantities differ' }
}
Write-Output "Finnish/English resource parity and format arguments passed for $($baseKeys.Count) resources."
Write-Output "Source hygiene and JSON/XML syntax passed for $($files.Count) files."
