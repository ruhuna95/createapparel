param([Parameter(Mandatory=$true)][string]$GeneratedTexture)
Add-Type -AssemblyName System.Drawing
$maskPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../src/main/resources/assets/createapparel/textures/garment/mask/face/base/default.png'))
$source = [Drawing.Bitmap]::new([IO.Path]::GetFullPath($GeneratedTexture))
$original = [Drawing.Bitmap]::new($maskPath)
$result = [Drawing.Bitmap]::new($original)
$original.Dispose()
function Get-RowShade([int]$row) {
    $values = for ($x = 8; $x -lt 16; $x++) {
        $pixel = $source.GetPixel([int][Math]::Floor(($x + 0.5) * $source.Width / 32),
            [int][Math]::Floor(($row + 0.5) * $source.Height / 32))
        if ($pixel.A -gt 128) { [int][Math]::Round(($pixel.R + $pixel.G + $pixel.B) / 3) }
    }
    if ($values.Count -eq 0) { throw "No opaque pixels in generated row $row" }
    return [int][Math]::Round(($values | Measure-Object -Average).Average)
}
$light = Get-RowShade 15
$dark = Get-RowShade 16
if ($light -le $dark + 8 -or $dark -lt 160) { throw 'Generated texture does not contain subtle horizontal pleats' }
foreach ($row in @(15, 16, 17)) {
    $shade = if ($row -eq 16) { $dark } else { $light }
    for ($x = 7; $x -le 16; $x++) {
        $result.SetPixel($x, $row, [Drawing.Color]::FromArgb(255, $shade, $shade, $shade))
    }
}
for ($x = 16; $x -lt 24; $x++) {
    $result.SetPixel($x, 0, [Drawing.Color]::FromArgb(255, $dark, $dark, $dark))
}
$source.Dispose()
$result.Save($maskPath, [Drawing.Imaging.ImageFormat]::Png)
$result.Dispose()
"Horizontal mask shades: light=$light dark=$dark"
