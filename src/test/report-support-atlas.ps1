param([Parameter(Mandatory = $true)][string[]]$Logs, [switch]$Csv)
$ErrorActionPreference = 'Stop'
$records = & (Join-Path $PSScriptRoot 'report-citizen-pipeline.ps1') -Logs $Logs -Csv | ConvertFrom-Csv
function Number($Value) { [double]::Parse($Value, [Globalization.CultureInfo]::InvariantCulture) }
function Change($Before, $After) {
    $baseline = Number $Before
    if ($baseline -le 0) { return [double]::NaN }
    100 * ((Number $After) / $baseline - 1)
}
$pairs = foreach ($group in ($records | Group-Object Log, Phase)) {
    $rows = @($group.Group)
    if ($rows.Count % 2 -ne 0) { throw 'An alternating comparison must contain complete pairs' }
    for ($index = 0; $index -lt $rows.Count; $index += 2) {
        $first = $rows[$index]; $second = $rows[$index + 1]
        $pattern = $first.Case -replace '-(3d|atlas)$', ''
        if (($second.Case -replace '-(3d|atlas)$', '') -ne $pattern) { throw 'Pair patterns differ' }
        $baseline = @($first, $second) | Where-Object Case -eq "$pattern-3d"
        $atlas = @($first, $second) | Where-Object Case -eq "$pattern-atlas"
        if (@($baseline).Count -ne 1 -or @($atlas).Count -ne 1) { throw 'Each pair must compare 3d and atlas exactly once' }
        [pscustomobject]@{
            Count = $first.Count; Pattern = $pattern; Phase = $first.Phase; Pair = 1 + $index / 2
            Order = if ($first.Case.EndsWith('-atlas')) { 'ON-OFF' } else { 'OFF-ON' }
            FpsOff = Number $baseline.Fps; FpsOn = Number $atlas.Fps; FpsChangePercent = Change $baseline.Fps $atlas.Fps
            LowOff = Number $baseline.Low1; LowOn = Number $atlas.Low1; LowChangePercent = Change $baseline.Low1 $atlas.Low1
            P95Off = Number $baseline.P95Ms; P95On = Number $atlas.P95Ms
            P99Off = Number $baseline.P99Ms; P99On = Number $atlas.P99Ms; P99ChangePercent = Change $baseline.P99Ms $atlas.P99Ms
            CitizenCpuOff = Number $baseline.CitizenCpu; CitizenCpuOn = Number $atlas.CitizenCpu
            DrawOff = Number $baseline.WorldDraws; DrawOn = Number $atlas.WorldDraws
            BindOff = Number $baseline.WorldBinds; BindOn = Number $atlas.WorldBinds
            FramesOff = Number $baseline.SampledIntervals; FramesOn = Number $atlas.SampledIntervals
            GcOff = Number $baseline.Gc; GcOn = Number $atlas.Gc
            GcTimeOffMs = Number $baseline.GcTimeMs; GcTimeOnMs = Number $atlas.GcTimeMs
            Log = $first.Log
        }
    }
}
if ($Csv) { $pairs | ConvertTo-Csv -NoTypeInformation }
else { $pairs | Format-Table Count, Pattern, Phase, Pair, Order, FpsOff, FpsOn, LowOff, LowOn, P99Off, P99On -AutoSize }
