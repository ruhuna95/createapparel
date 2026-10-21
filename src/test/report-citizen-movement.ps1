param([Parameter(Mandatory = $true)][string[]]$Logs, [switch]$Csv)
$ErrorActionPreference = 'Stop'
function Read-Metric([string]$Text, [string]$Name) {
    $match = [regex]::Match($Text, '(?:^|[\s;])' + [regex]::Escape($Name) + '=(-?[\d.]+|NaN)')
    if (!$match.Success) { return [double]::NaN }
    return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}
function Read-Cpu([string]$Text, [string]$Name) {
    $match = [regex]::Match($Text, '(?m)^' + [regex]::Escape($Name) + ' .*? ms/renderFrame=([\d.]+)')
    if (!$match.Success) { return [double]::NaN }
    return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}
$rows = foreach ($path in $Logs) {
    $text = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $path))
    $matches = [regex]::Matches($text, 'CITIZEN_MOVEMENT_PROFILE case=(\S+) count=(\d+) phase=(\d+)')
    for ($i = 0; $i -lt $matches.Count; $i++) {
        $start = $matches[$i].Index
        $end = if ($i + 1 -lt $matches.Count) { $matches[$i + 1].Index } else { $text.Length }
        $block = $text.Substring($start, $end - $start)
        $cpu = [regex]::Match($block, '(?m)^citizenRenderer .*? ms/renderFrame=([\d.]+)')
        [pscustomobject]@{
            Count = [int]$matches[$i].Groups[2].Value
            Case = $matches[$i].Groups[1].Value
            Phase = [int]$matches[$i].Groups[3].Value
            Detailed = $block.Contains('detailTiming=true')
            Fps = Read-Metric $block 'fps'
            Low1 = Read-Metric $block 'fps1PercentLow'
            MeanMs = Read-Metric $block 'meanFrameMs'
            P95Ms = Read-Metric $block 'frameP95Ms'
            P99Ms = Read-Metric $block 'frameP99Ms'
            MaxMs = Read-Metric $block 'frameMaxMs'
            CitizenCpuMs = if ($cpu.Success) { [double]::Parse($cpu.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture) } else { [double]::NaN }
            PoseCpuMs = Read-Cpu $block 'citizenModelPose'
            GarmentCpuMs = Read-Cpu $block 'citizenGarment'
            PoseSamplingCpuMs = Read-Cpu $block 'citizenPoseSampling'
            FrameCpuMs = Read-Cpu $block 'apparelRenderFrame'
            EnvelopePoseCpuMs = Read-Cpu $block 'envelopePoseBuild'
            DeformCpuMs = Read-Cpu $block 'envelopeVertexTransform'
            NormalsCpuMs = Read-Cpu $block 'envelopeFaceNormals'
            RenderTransformCpuMs = Read-Cpu $block 'envelopeRenderTransform'
            VertexWriteCpuMs = Read-Cpu $block 'envelopeVertexWrite'
            PoseHit = Read-Metric $block 'citizenPoseCacheHit'
            PoseMiss = Read-Metric $block 'citizenPoseCacheMiss'
            PoseUncached = Read-Metric $block 'citizenPoseUncached'
            DeformHit = Read-Metric $block 'citizenDeformationCacheHit'
            DeformMiss = Read-Metric $block 'citizenDeformationCacheMiss'
            AllocBytes = Read-Metric $block 'renderThreadAllocatedBytesPerFrame'
            Gc = Read-Metric $block 'gcCollections'
            Switches = Read-Metric $block 'citizenLodChanges'
            TransitionIntervals = Read-Metric $block 'lodTransitionIntervals'
            TransitionP99 = Read-Metric $block 'transitionP99Ms'
            MeshBuilds = Read-Metric $block 'meshBuilds'
            SpriteBuilds = Read-Metric $block 'spriteBuilds'
            Evictions = Read-Metric $block 'evictions'
            Fallback = Read-Metric $block 'fallback3d'
            Entries = Read-Metric $block 'entries'
            Log = $path
        }
    }
}
if ($Csv) { $rows | ConvertTo-Csv -NoTypeInformation }
else { $rows | Format-Table Count, Case, Phase, Detailed, Fps, Low1, P95Ms, P99Ms, MaxMs, CitizenCpuMs, AllocBytes, Gc -AutoSize }
