param([switch]$Apply)
$ErrorActionPreference='Stop'
$repository='MG-Trener/MGym'
$environment='google-play'
if (!$Apply) {
    Write-Output "With -Apply: upload only four signing secrets to $repository / $environment. No passwords are printed."
    exit 0
}
$privatePath=Join-Path (Split-Path $PSScriptRoot -Parent) '.private'
$config=Get-Content -LiteralPath (Join-Path $privatePath 'play-upload.json') -Raw | ConvertFrom-Json
$secure=ConvertTo-SecureString $config.password
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $password=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $values=@{
        MGYM_UPLOAD_KEYSTORE_BASE64=[Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $privatePath 'play-upload.p12')))
        MGYM_UPLOAD_STORE_PASSWORD=$password
        MGYM_UPLOAD_KEY_PASSWORD=$password
        MGYM_UPLOAD_ALIAS=$config.alias
    }
    foreach ($entry in $values.GetEnumerator()) {
        $entry.Value | gh secret set $entry.Key --repo $repository --env $environment
        if ($LASTEXITCODE -ne 0) { throw ('Failed to configure secret '+$entry.Key) }
        Write-Output ('Configured '+$entry.Key)
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $secure.Dispose();$password=$null;$values=$null
}
