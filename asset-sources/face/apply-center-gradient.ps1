param([Parameter(Mandatory=$true)][string]$GeneratedTexture)
Add-Type -AssemblyName System.Drawing
$source = [Drawing.Bitmap]::new([IO.Path]::GetFullPath($GeneratedTexture))
$baseline = [Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'mask-before-center-gradient.png'))
$result = [Drawing.Bitmap]::new($baseline)
$left = $source.Width
$right = -1
$top = $source.Height
$bottom = -1
for ($y = [int]($source.Height * 0.25); $y -lt [int]($source.Height * 0.8); $y++) {
    for ($x = 0; $x -lt $source.Width; $x++) {
        if ($source.GetPixel($x, $y).A -gt 200) {
            $left = [Math]::Min($left, $x)
            $right = [Math]::Max($right, $x)
            $top = [Math]::Min($top, $y)
            $bottom = [Math]::Max($bottom, $y)
        }
    }
}
if ($right -le $left -or $bottom -le $top) { throw 'Generated mask rectangle was not found' }
$profile = @(for ($i = 0; $i -lt 8; $i++) {
    $x = [int]($left + ($i + 1.5) * ($right - $left + 1) / 10)
    $samples = foreach ($fraction in @(0.17, 0.5, 0.83)) {
        $y = [int]($top + $fraction * ($bottom - $top))
        $pixel = $source.GetPixel($x, $y)
        if ($pixel.A -lt 200) { throw 'Transparent gradient sample' }
        ($pixel.R + $pixel.G + $pixel.B) / 3.0
    }
    ($samples | Measure-Object -Average).Average
})
for ($i = 0; $i -lt 4; $i++) {
    $value = ($profile[$i] + $profile[7 - $i]) / 2
    $profile[$i] = $value
    $profile[7 - $i] = $value
}
if ($profile[3] -le $profile[0]) { throw 'Generated gradient does not brighten toward the center' }
$edge = $profile[0]
$center = $profile[3]
$previous = 0.94
for ($i = 0; $i -lt 4; $i++) {
    $factor = 0.94 + 0.10 * [Math]::Min(1.0, [Math]::Max(0.0, ($profile[$i] - $edge) / ($center - $edge)))
    $factor = [Math]::Max($previous, $factor)
    $profile[$i] = $factor
    $profile[7 - $i] = $factor
    $previous = $factor
}
for ($y = 0; $y -lt $baseline.Height; $y++) {
    for ($x = 0; $x -lt $baseline.Width; $x++) {
        $pixel = $baseline.GetPixel($x, $y)
        if ($pixel.A -eq 0) { continue }
        $index = if ($y -eq 0) { $x - 16 } else { [Math]::Min(7, [Math]::Max(0, $x - 8)) }
        if ($index -lt 0 -or $index -gt 7) { throw 'Unexpected opaque UV pixel' }
        $shade = [int][Math]::Round($pixel.R * $profile[$index])
        $result.SetPixel($x, $y, [Drawing.Color]::FromArgb($pixel.A, $shade, $shade, $shade))
    }
}
$target = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../src/main/resources/assets/createapparel/textures/garment/mask/face/base/default.png'))
$result.Save($target, [Drawing.Imaging.ImageFormat]::Png)
$result.Dispose()
$baseline.Dispose()
$source.Dispose()
"Gradient profile: $($profile -join ', ')"
