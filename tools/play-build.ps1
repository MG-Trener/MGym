param([switch]$Initialize, [string[]]$Tasks = @(':app:bundleRelease', ':app:assembleRelease', ':app:lintRelease', ':app:testDebugUnitTest'), [string[]]$GradleOptions = @())
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$privatePath = Join-Path $projectRoot '.private'
$configPath = Join-Path $privatePath 'play-upload.json'
$storePath = Join-Path $privatePath 'play-upload.p12'
$javaPath = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }
$keytool = Join-Path $javaPath 'bin\keytool.exe'
if (!(Test-Path -LiteralPath $keytool)) { throw 'Set JAVA_HOME to Android Studio JBR or JDK 25.' }
$keys = @('JAVA_HOME','MGYM_UPLOAD_STORE','MGYM_UPLOAD_STORE_PASSWORD','MGYM_UPLOAD_ALIAS','MGYM_UPLOAD_KEY_PASSWORD')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
function Unlock-LocalPassword([string]$Protected) {
    $secure = ConvertTo-SecureString $Protected
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
}
try {
    $env:JAVA_HOME = $javaPath
    if ($Initialize -and !(Test-Path -LiteralPath $configPath) -and !(Test-Path -LiteralPath $storePath)) {
        New-Item -ItemType Directory -Path $privatePath -Force | Out-Null
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl = [Security.AccessControl.DirectorySecurity]::new()
        $acl.SetOwner($sid); $acl.SetAccessRuleProtection($true, $false)
        foreach ($identity in @($sid, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
            $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
        }
        Set-Acl -LiteralPath $privatePath -AclObject $acl
        $random = [byte[]]::new(36)
        [Security.Cryptography.RandomNumberGenerator]::Fill($random)
        $env:MGYM_UPLOAD_STORE_PASSWORD = [Convert]::ToBase64String($random)
        $env:MGYM_UPLOAD_KEY_PASSWORD = $env:MGYM_UPLOAD_STORE_PASSWORD
        $env:MGYM_UPLOAD_ALIAS = 'mgym-upload'
        & $keytool -genkeypair -storetype PKCS12 -keystore $storePath -storepass:env MGYM_UPLOAD_STORE_PASSWORD -keypass:env MGYM_UPLOAD_KEY_PASSWORD -alias $env:MGYM_UPLOAD_ALIAS -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=MGym Upload, OU=MGym, O=MG-Trener'
        if ($LASTEXITCODE -ne 0) { throw 'Upload key generation failed. Existing files were preserved.' }
        $secure = ConvertTo-SecureString $env:MGYM_UPLOAD_STORE_PASSWORD -AsPlainText -Force
        try { $protected = ConvertFrom-SecureString $secure } finally { $secure.Dispose() }
        @{version=1; alias=$env:MGYM_UPLOAD_ALIAS; password=$protected} | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding utf8
    }
    if (!(Test-Path -LiteralPath $configPath) -or !(Test-Path -LiteralPath $storePath)) { throw 'Local upload key missing. Run with -Initialize once. No existing key is overwritten.' }
    $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    $env:MGYM_UPLOAD_STORE = $storePath
    $env:MGYM_UPLOAD_ALIAS = $config.alias
    $env:MGYM_UPLOAD_STORE_PASSWORD = Unlock-LocalPassword $config.password
    $env:MGYM_UPLOAD_KEY_PASSWORD = $env:MGYM_UPLOAD_STORE_PASSWORD
    & $keytool -exportcert -rfc -keystore $storePath -storepass:env MGYM_UPLOAD_STORE_PASSWORD -alias $env:MGYM_UPLOAD_ALIAS -file (Join-Path $privatePath 'upload-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read upload certificate.' }
    Push-Location $projectRoot
    try {
        & .\gradlew.bat @Tasks @GradleOptions --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Play build or verification failed.' }
    } finally { Pop-Location }
    Write-Output 'Signed Google Play build finished. Signing passwords were not printed or saved as plain text.'
} finally {
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
