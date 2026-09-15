# package_exe.ps1
# This script bundles the AMQ Simulator into a standalone Windows .exe (App Image).
# It requires JDK 14+ (currently using JDK 17).

# 1. Configuration
$APP_NAME = "Expleo_Pacs_Simulator"
$VERSION = "0.0.1"
$MAIN_CLASS = "com.expleo.simulator.SimulatorControlUI"
$INPUT_DIR = "target/package-input"
$OUTPUT_DIR = "target/package-output"
$FAT_JAR_PATTERN = "*with-dependencies.jar"

# 2. Preparation
Write-Host "--- Preparing Packaging Environment ---" -ForegroundColor Cyan
if (!(Test-Path "target")) {
    Write-Error "target/ directory not found. Please run 'mvn clean package' first."
    exit 1
}

# Clear previous output
if (Test-Path $INPUT_DIR) { Remove-Item -Recurse -Force $INPUT_DIR }
if (Test-Path $OUTPUT_DIR) { Remove-Item -Recurse -Force $OUTPUT_DIR }
New-Item -ItemType Directory -Path $INPUT_DIR | Out-Null

# Find the fat JAR
$FAT_JAR = Get-ChildItem "target" -Filter $FAT_JAR_PATTERN | Select-Object -First 1
if (!$FAT_JAR) {
    Write-Error "Fat JAR (with-dependencies.jar) not found in target/. Please run 'mvn clean package' first."
    exit 1
}

# Copy JAR and template resources to input directory
Write-Host "Copying $FAT_JAR to $INPUT_DIR..."
Copy-Item $FAT_JAR.FullName -Destination $INPUT_DIR
# Copy resources if needed (e.g., Template/config.json)
if (Test-Path "Template") {
    Write-Host "Copying Template/ directory..."
    Copy-Item -Recurse "Template" -Destination $INPUT_DIR
}

# 3. Packaging
Write-Host "--- Packaging Application into EXE ---" -ForegroundColor Cyan
jpackage `
  --type app-image `
  --dest $OUTPUT_DIR `
  --name $APP_NAME `
  --input $INPUT_DIR `
  --main-jar $FAT_JAR.Name `
  --main-class $MAIN_CLASS `
  --app-version $VERSION `
  --vendor "Expleo - Manojh" `
  --copyright "Copyright 2026 Expleo" `
  --win-console `
  --description "PACS Message Simulator"

if ($LASTEXITCODE -eq 0) {
    Write-Host "--- SUCCESS ---" -ForegroundColor Green
    Write-Host "The .exe has been created in: $OUTPUT_DIR/$APP_NAME"
    Write-Host "You can run it via: $OUTPUT_DIR/$APP_NAME/$APP_NAME.exe"
} else {
    Write-Error "jpackage failed with exit code $LASTEXITCODE"
}
