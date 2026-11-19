<#
.SYNOPSIS
    Maven Wrapper script for PowerShell
#>

[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArguments
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Locate Java executable
$javaCmd = $null
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $javaCmd = "$env:JAVA_HOME\bin\java.exe"
} else {
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) {
        $javaCmd = $cmd.Source
    }
}

if (-not $javaCmd) {
    Write-Error "JAVA_HOME is not set and java.exe was not found in PATH."
    exit 1
}

$wrapperJar = Join-Path $scriptDir ".mvn\wrapper\maven-wrapper.jar"
$wrapperProps = Join-Path $scriptDir ".mvn\wrapper\maven-wrapper.properties"

if (-not (Test-Path $wrapperJar)) {
    if (Test-Path $wrapperProps) {
        $props = Get-Content $wrapperProps | ConvertFrom-StringData
        $wrapperUrl = $props.wrapperUrl
        if ($wrapperUrl) {
            Write-Host "Downloading Maven Wrapper JAR..."
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $wrapperUrl -OutFile $wrapperJar
        }
    }
}

if (-not (Test-Path $wrapperJar)) {
    Write-Error "Could not locate or download maven-wrapper.jar at: $wrapperJar"
    exit 1
}

# Run Maven Wrapper via Java specifying the main class explicitly
& $javaCmd "-Dmaven.multiModuleProjectDirectory=$scriptDir" -classpath $wrapperJar org.apache.maven.wrapper.MavenWrapperMain @MavenArguments
exit $LASTEXITCODE
