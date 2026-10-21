param([string[]]$Logs = @())

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($Logs.Count -eq 0) { $Logs = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build') -Filter 'envelope-*-client.log' | Select-Object -ExpandProperty FullName) }
function Metric([string]$Text, [string]$Name) {
    $match = [regex]::Match($Text, [regex]::Escape($Name) + '=([-+0-9.E]+|NaN)')
    if (!$match.Success) { return [double]::NaN }
    return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}
function Section([string]$Text, [string]$Name) {
    $line = [regex]::Match($Text, '(?m)^' + [regex]::Escape($Name) + ' calls=[^\r\n]*').Value
    return Metric $line 'ms/renderFrame'
}
$rows = foreach ($log in $Logs) {
    $raw = Get-Content -LiteralPath $log -Raw -Encoding UTF8
    foreach ($block in [regex]::Matches($raw, '(?s)ENVELOPE_COMPARISON phase=(\d+) backend=(\w+) mode=(\w+)\r?\n(.*?)(?=\r?\n\[[^\r\n]*CITIZEN_CACHE_RELEASE|\z)')) {
        $body = $block.Groups[4].Value
        $case = [regex]::Match($body, 'CITIZEN_FPS_PROFILE case=(\S+)').Groups[1].Value
        if (!$case) { throw "Profile block missing in $log" }
        $frames = Metric $body 'intervals'
        $perFrame = if ($frames -gt 0) { $frames } else { [double]::NaN }
        [pscustomobject][ordered]@{
            Run = [IO.Path]::GetFileNameWithoutExtension($log); Count = Metric $body 'loadedServerCitizens'
            Backend = $block.Groups[2].Value; Mode = $block.Groups[3].Value; Case = $case
            FPS = Metric $body 'fps'; Low = Metric $body 'fps1PercentLow'; MeanMs = Metric $body 'meanFrameMs'
            P95Ms = Metric $body 'frameP95Ms'; P99Ms = Metric $body 'frameP99Ms'; Samples = Metric $body 'sampledIntervals'
            CitizenMs = Section $body 'citizenRenderer'; GarmentMs = Section $body 'citizenGarment'; EnvelopeMs = Section $body 'envelopeGeometrySubmission'
            PoseMs = Section $body 'envelopePoseBuild'; DeformMs = Section $body 'envelopeVertexTransform'; NormalsMs = Section $body 'envelopeFaceNormals'
            MatrixMs = Section $body 'envelopeRenderTransform'; WriteMs = Section $body 'envelopeVertexWrite'
            BodyMs = Section $body 'citizenBodySupport'; UnderwearMs = Section $body 'citizenUnderwearSupport'; HeadMs = Section $body 'citizenHead'; CosmeticMs = Section $body 'citizenCosmetic'
            AllocBytes = Metric $body 'renderThreadAllocatedBytesPerFrame'
            Vertices = (Metric $body 'submittedVertices') / $perFrame; EnvelopeVertices = (Metric $body 'envelopeSubmittedVertices') / $perFrame
            UniquePositions = (Metric $body 'envelopeUniquePositions') / $perFrame; Corners = (Metric $body 'envelopeCornerReferences') / $perFrame
            Parts = (Metric $body 'citizenGarmentParts') / $perFrame; TypeGets = (Metric $body 'envelopeRenderTypeGets') / $perFrame
            GarmentCache = Metric $body 'garmentEntries'; HeadCache = Metric $body 'citizenTextureEntries'
            OcclusionMs = Section $body 'supportOcclusion'; BodyOmitted = (Metric $body 'bodyOmittedParts') / $perFrame
            UnderwearOmitted = (Metric $body 'underwearOmittedParts') / $perFrame
            Lod0 = (Metric $body 'lod0Citizens') / $perFrame; Lod1 = (Metric $body 'lod1Citizens') / $perFrame
            Lod2 = (Metric $body 'lod2Citizens') / $perFrame; Lod3 = (Metric $body 'lod3Citizens') / $perFrame
            Lod0Ms = Section $body 'lod0Garment'; Lod1Ms = Section $body 'lod1Garment'; Lod2Ms = Section $body 'lod2Garment'; Lod3Ms = Section $body 'lod3Garment'
            GeometryMeshes = Metric $body 'observedGeometryMeshes'; TopologyBytes = Metric $body 'observedTopologyBytes'
            CachedMeshes = Metric $body 'cachedEnvelopeMeshes'; CachedTopologyBytes = Metric $body 'cachedEnvelopeTopologyBytes'
            LodChanges = Metric $body 'citizenLodChanges'; CacheMiss = Metric $body 'garmentCache.MISS'; CacheHit = Metric $body 'garmentCache.HIT'
            TextureComposeMs = Section $body 'garmentTextureComposeInclusive'; TextureUploadMs = Section $body 'garmentTextureUpload'
            TypeChanges = (Metric $body 'renderTypeRequestsChanged') / $perFrame
            TextureChanges = (Metric $body 'textureBindingRequestsChanged') / $perFrame
            ReflectionCalls = (Metric $body 'reflectionCitizenCalls') / $perFrame
            ReflectionMs = Section $body 'reflectionCitizenRenderer'; PrimaryMs = Section $body 'primaryCitizenRenderer'
            Lod0CitizenMs = Section $body 'lod0Citizen'; Lod1CitizenMs = Section $body 'lod1Citizen'
            Lod2CitizenMs = Section $body 'lod2Citizen'; Lod3CitizenMs = Section $body 'lod3Citizen'
        }
    }
}
$rows
