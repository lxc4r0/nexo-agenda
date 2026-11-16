param([string]$Destination = (Join-Path $PSScriptRoot '../../entrega-nexo'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$delivery = [IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $delivery -Force | Out-Null

function Add-Tree($Archive, [string]$Directory, [string]$Prefix, [string[]]$Exclude = @()) {
    foreach ($item in Get-ChildItem -LiteralPath $Directory -Force) {
        if ($item.Name -in $Exclude -or $item.Name.EndsWith('.log') -or $item.Name -eq 'local.properties') { continue }
        $name = $Prefix + $item.Name
        if ($item.PSIsContainer) { Add-Tree $Archive $item.FullName ($name + '/') $Exclude }
        else { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($Archive, $item.FullName, $name, [IO.Compression.CompressionLevel]::Optimal) | Out-Null }
    }
}
function Zip-Tree([string]$Directory, [string]$Name, [string]$Prefix = '', [string[]]$Exclude = @()) {
    $target = Join-Path $delivery $Name
    $stream = [IO.File]::Open($target, [IO.FileMode]::Create)
    $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
    try { Add-Tree $archive $Directory $Prefix $Exclude } finally { $archive.Dispose(); $stream.Dispose() }
}

Zip-Tree $project 'nexo-agenda-proyecto.zip' 'nexo-agenda/' @('.gradle','.kotlin','.bootstrap','.idea','.git','build','data','node_modules')
Zip-Tree (Join-Path $project 'diseno-figma') 'nexo-pantallas-figma.zip' 'diseno-figma/'
Copy-Item -LiteralPath (Join-Path $project 'androidApp/build/outputs/apk/debug/androidApp-debug.apk') -Destination (Join-Path $delivery 'nexo-agenda-android.apk') -Force
Zip-Tree (Join-Path $project 'desktopApp/build/compose/binaries/main/app/NexoAgenda') 'nexo-agenda-windows.zip' 'NexoAgenda/'
Zip-Tree (Join-Path $project 'webApp/build/dist/wasmJs/productionExecutable') 'nexo-agenda-web.zip' ''
Copy-Item -LiteralPath (Join-Path $project 'README.md') -Destination (Join-Path $delivery 'LEEME.md') -Force
Get-ChildItem -LiteralPath $delivery | Select-Object Name,Length
