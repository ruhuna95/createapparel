param([Parameter(Mandatory = $true)][string[]]$Logs, [switch]$Csv)
$ErrorActionPreference = 'Stop'
function Metric([string]$Text, [string]$Name) {
    $match = [regex]::Match($Text, '(?:^|[\s,{])' + [regex]::Escape($Name) + '=(-?[\d.]+|NaN)')
    if (!$match.Success) { return [double]::NaN }
    return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}
function Cpu([string]$Text, [string]$Name) {
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
        $intervals = Metric $block 'intervals'
        $denominator = if ($intervals -gt 0) { $intervals } else { [double]::NaN }
        [pscustomobject]@{
            Count = [int]$matches[$i].Groups[2].Value; Case = $matches[$i].Groups[1].Value; Phase = [int]$matches[$i].Groups[3].Value
            Trial = Metric $block 'trial'; WarmupTicks = Metric $block 'warmupTicks'; SampleTicks = Metric $block 'sampleTicks'
            Detailed = $block.Contains('detailTiming=true')
            Fps = Metric $block 'fps'; Low1 = Metric $block 'fps1PercentLow'; MeanMs = Metric $block 'meanFrameMs'
            P95Ms = Metric $block 'frameP95Ms'; P99Ms = Metric $block 'frameP99Ms'; MaxMs = Metric $block 'frameMaxMs'
            SampledIntervals = Metric $block 'sampledIntervals'
            Frames = $intervals; CitizenCalls = (Metric $block 'citizenRenderCalls') / $denominator
            PartRequests = (Metric $block 'renderTypeRequests') / $denominator
            SourceRequests = (Metric $block 'pipeline.requests') / $denominator
            RequestChanges = (Metric $block 'pipeline.requestChanges') / $denominator
            NonemptyEnds = (Metric $block 'pipeline.nonemptyBufferEnds') / $denominator
            BufferEnds = (Metric $block 'pipeline.bufferEnds') / $denominator
            WorldDraws = (Metric $block 'pipeline.glDrawElements') / $denominator
            MainDraws = (Metric $block 'pipeline.main.glDrawElements') / $denominator
            ReflectionDraws = (Metric $block 'pipeline.reflection.glDrawElements') / $denominator
            WorldBinds = (Metric $block 'pipeline.glTextureBinds') / $denominator
            WorldVertices = (Metric $block 'pipeline.uploadedVertices') / $denominator
            SupportGarmentVertices = (Metric $block 'submittedVertices') / $denominator
            Lod0 = (Metric $block 'lod0Citizens') / $denominator; Lod1 = (Metric $block 'lod1Citizens') / $denominator
            Lod2 = (Metric $block 'lod2Citizens') / $denominator; Lod3 = (Metric $block 'lod3Citizens') / $denominator
            ReflectionCalls = (Metric $block 'reflectionCitizenCalls') / $denominator
            GarmentCacheHit = Metric $block 'garmentCache.HIT'; GarmentCacheMiss = Metric $block 'garmentCache.MISS'
            CitizenCacheHit = Metric $block 'citizenCache.HIT'; CitizenCacheMiss = Metric $block 'citizenCache.MISS'
            CitizenCpu = Cpu $block 'citizenRenderer'; BodyCpu = Cpu $block 'citizenBodySupport'; UnderwearCpu = Cpu $block 'citizenUnderwearSupport'
            HeadCpu = Cpu $block 'citizenHead'; CosmeticCpu = Cpu $block 'citizenCosmetic'; GarmentCpu = Cpu $block 'citizenGarment'
            EnvelopeCpu = Cpu $block 'envelopeGeometrySubmission'; VertexWriteCpu = Cpu $block 'envelopeVertexWrite'; BulkCopyCpu = Cpu $block 'pipelineBulkCopy'
            AllocBytes = Metric $block 'renderThreadAllocatedBytesPerFrame'; Gc = Metric $block 'gcCollections'
            GcTimeMs = Metric $block 'gcTimeMs'; HeapUsedBytes = Metric $block 'heapUsedBytes'; DirectBufferBytes = Metric $block 'jvmDirectBufferBytes'
            Log = $path
        }
    }
}
if ($Csv) { $rows | ConvertTo-Csv -NoTypeInformation }
else { $rows | Format-Table Count, Case, Phase, Detailed, Fps, Low1, P99Ms, SourceRequests, NonemptyEnds, WorldDraws, WorldBinds -AutoSize }
