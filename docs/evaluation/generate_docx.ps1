$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$reportPath = Join-Path $root "project_evaluation_report.md"
$viewsPath = Join-Path $root "architecture_views_mermaid.md"
$adrPath = Join-Path $root "architecture_decision_log.md"
$outPath = Join-Path $root "Project_Evaluation_Report.docx"

$reportText = Get-Content -Path $reportPath -Raw
$viewsText = Get-Content -Path $viewsPath -Raw
$adrText = Get-Content -Path $adrPath -Raw

$combined = @"
CodeReboot Project - Evaluation and Architecture Views
Generated: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

================================================================
RUBRIC-BASED EVALUATION REPORT
================================================================

$reportText

================================================================
MERMAID ARCHITECTURE VIEWS
================================================================

$viewsText

================================================================
ARCHITECTURE DECISION LOG
================================================================

$adrText
"@

function Convert-ToWordParagraphs([string]$text) {
    $xmlEscape = {
        param([string]$value)
        [System.Security.SecurityElement]::Escape($value)
    }

    $paragraphs = New-Object System.Collections.Generic.List[string]
    $normalized = $text -replace "`r`n", "`n"
    foreach ($line in ($normalized -split "`n")) {
        $safe = & $xmlEscape $line
        if ([string]::IsNullOrWhiteSpace($safe)) {
            $paragraphs.Add('<w:p/>')
        } else {
          $paragraphs.Add('<w:p><w:r><w:t xml:space="preserve">' + $safe + '</w:t></w:r></w:p>')
        }
    }

    return ($paragraphs -join "`n")
}

$docBody = Convert-ToWordParagraphs $combined

$contentTypes = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>
'@

$rels = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>
'@

$documentXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:wp14="http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:w10="urn:schemas-microsoft-com:office:word" xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml" xmlns:wpg="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup" xmlns:wpi="http://schemas.microsoft.com/office/word/2010/wordprocessingInk" xmlns:wne="http://schemas.microsoft.com/office/word/2006/wordml" xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape" mc:Ignorable="w14 wp14">
  <w:body>
$docBody
    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/>
      <w:cols w:space="708"/>
      <w:docGrid w:linePitch="360"/>
    </w:sectPr>
  </w:body>
</w:document>
"@

$coreXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>CodeReboot Project Evaluation Report</dc:title>
  <dc:creator>GitHub Copilot</dc:creator>
  <cp:lastModifiedBy>GitHub Copilot</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">$(Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$(Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")</dcterms:modified>
</cp:coreProperties>
"@

$appXml = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>Microsoft Office Word</Application>
  <DocSecurity>0</DocSecurity>
  <ScaleCrop>false</ScaleCrop>
  <HeadingPairs>
    <vt:vector size="2" baseType="variant">
      <vt:variant><vt:lpstr>Title</vt:lpstr></vt:variant>
      <vt:variant><vt:i4>1</vt:i4></vt:variant>
    </vt:vector>
  </HeadingPairs>
  <TitlesOfParts>
    <vt:vector size="1" baseType="lpstr">
      <vt:lpstr>Document</vt:lpstr>
    </vt:vector>
  </TitlesOfParts>
  <Company></Company>
  <LinksUpToDate>false</LinksUpToDate>
  <SharedDoc>false</SharedDoc>
  <HyperlinksChanged>false</HyperlinksChanged>
  <AppVersion>16.0000</AppVersion>
</Properties>
'@

if (Test-Path $outPath) {
    Remove-Item $outPath -Force
}

$tempDir = Join-Path $root "_docx_tmp"
if (Test-Path $tempDir) {
    Remove-Item $tempDir -Recurse -Force
}

New-Item -ItemType Directory -Path $tempDir | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tempDir "_rels") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tempDir "word") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tempDir "docProps") | Out-Null

$contentTypes | Out-File -LiteralPath (Join-Path $tempDir "[Content_Types].xml") -Encoding utf8
$rels | Out-File -LiteralPath (Join-Path $tempDir "_rels\\.rels") -Encoding utf8
$documentXml | Out-File -LiteralPath (Join-Path $tempDir "word\\document.xml") -Encoding utf8
$coreXml | Out-File -LiteralPath (Join-Path $tempDir "docProps\\core.xml") -Encoding utf8
$appXml | Out-File -LiteralPath (Join-Path $tempDir "docProps\\app.xml") -Encoding utf8

Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($tempDir, $outPath)

Remove-Item $tempDir -Recurse -Force
Write-Output "DOCX_CREATED: $outPath"
