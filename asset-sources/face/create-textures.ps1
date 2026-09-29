param([switch]$ReplaceGeneratedTextures)
Add-Type -AssemblyName System.Drawing
$assetRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../src/main/resources/assets/createapparel/textures/garment'))
function Save-FaceTexture($bitmap, $relative) {
    $target = Join-Path $assetRoot $relative
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
    if ((Test-Path -LiteralPath $target) -and -not $ReplaceGeneratedTextures) { throw "Texture already exists: $target" }
    $bitmap.Save($target, [Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}
$frame = [Drawing.Bitmap]::new(8, 8)
foreach ($pixel in @(@(0,4),@(1,4),@(2,4),@(5,4),@(6,4),@(7,4),@(0,5),@(3,5),@(4,5),@(7,5))) {
    $shade = 220 + (($pixel[0] * 7 + $pixel[1] * 3) % 24)
    $frame.SetPixel($pixel[0], $pixel[1], [Drawing.Color]::FromArgb(255, $shade, $shade, $shade))
}
Save-FaceTexture $frame 'glasses/face/base/glasses.png'
foreach ($variant in @('glasses', 'sunglasses')) {
    $lens = [Drawing.Bitmap]::new(8, 8)
    foreach ($pixel in @(@(1,5),@(2,5),@(5,5),@(6,5),@(1,6),@(2,6),@(5,6),@(6,6))) {
        $shade = ($pixel[0] + $pixel[1]) % 3
        $color = if ($variant -eq 'glasses') {
            [Drawing.Color]::FromArgb(32 + $shade * 3, 248, 250, 252)
        } else {
            [Drawing.Color]::FromArgb(192 + $shade * 4, 49 + $shade * 3, 32 + $shade * 2, 23 + $shade)
        }
        $lens.SetPixel($pixel[0], $pixel[1], $color)
    }
    Save-FaceTexture $lens "glasses/face/overlay/$variant.png"
}
$mask = [Drawing.Bitmap]::new(32, 32)
for ($x = 0; $x -lt 8; $x++) {
    $shade = 224 + (($x * 7) % 19)
    $mask.SetPixel(8 + $x, 15, [Drawing.Color]::FromArgb(255, $shade, $shade, $shade))
    $shade -= 16
    $mask.SetPixel(16 + $x, 0, [Drawing.Color]::FromArgb(255, $shade, $shade, $shade))
}
$mask.SetPixel(7, 15, [Drawing.Color]::FromArgb(255, 220, 220, 220))
$mask.SetPixel(16, 15, [Drawing.Color]::FromArgb(255, 225, 225, 225))
Save-FaceTexture $mask 'mask/face/base/default.png'
