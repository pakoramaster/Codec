param(
    [string]$Repository = "pakoramaster/Codec",
    [string]$KeyAlias = "codec"
)

$ErrorActionPreference = "Stop"

function Read-ConfirmedSecret {
    param([Parameter(Mandatory)][string]$Prompt)

    $first = Read-Host $Prompt -AsSecureString
    $second = Read-Host "Confirm $Prompt" -AsSecureString
    $firstText = [System.Net.NetworkCredential]::new("", $first).Password
    $secondText = [System.Net.NetworkCredential]::new("", $second).Password

    if ([string]::IsNullOrWhiteSpace($firstText)) {
        throw "$Prompt cannot be empty."
    }
    if ($firstText -cne $secondText) {
        throw "$Prompt values did not match."
    }

    return $firstText
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $repositoryRoot "signing"
$keystorePath = Join-Path $signingDirectory "codec-release.jks"
$certificatePath = Join-Path $signingDirectory "codec-release-certificate.pem"

if (Test-Path -LiteralPath $keystorePath) {
    throw "Refusing to overwrite the existing keystore at $keystorePath"
}

$keytool = Get-Command keytool -ErrorAction Stop
$gh = Get-Command gh -ErrorAction Stop
& $gh.Source auth status
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI is not authenticated. Run 'gh auth login' first."
}

$storePassword = Read-ConfirmedSecret "Keystore password"
$keyPassword = Read-ConfirmedSecret "Key password"

New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null

& $keytool.Source -genkeypair -v `
    -keystore $keystorePath `
    -storetype JKS `
    -storepass $storePassword `
    -keypass $keyPassword `
    -alias $KeyAlias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname "CN=Codec, O=Codec, C=CA" `
    -noprompt
if ($LASTEXITCODE -ne 0) {
    throw "keytool failed to generate the release keystore."
}

& $keytool.Source -exportcert -rfc `
    -keystore $keystorePath `
    -storepass $storePassword `
    -alias $KeyAlias `
    -file $certificatePath
if ($LASTEXITCODE -ne 0) {
    throw "keytool failed to export the public certificate."
}

$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))
$keystoreBase64 | & $gh.Source secret set ANDROID_KEYSTORE_BASE64 --repo $Repository
$storePassword | & $gh.Source secret set ANDROID_KEYSTORE_PASSWORD --repo $Repository
$KeyAlias | & $gh.Source secret set ANDROID_KEY_ALIAS --repo $Repository
$keyPassword | & $gh.Source secret set ANDROID_KEY_PASSWORD --repo $Repository

if ($LASTEXITCODE -ne 0) {
    throw "One or more GitHub secrets could not be saved."
}

$keystoreBase64 = $null
$storePassword = $null
$keyPassword = $null

Write-Host "Release signing secrets configured for $Repository."
Write-Host "Back up this private keystore offline: $keystorePath"
Write-Host "The public certificate is available at: $certificatePath"
Write-Host "Do not delete the keystore or forget its alias and passwords."
