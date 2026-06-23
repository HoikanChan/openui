$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$classes = Join-Path $root "target/classes"
$jar = Join-Path $root "target/com.huawei.bsp.commonlib.resetclient-25.590.54.jar"

New-Item -ItemType Directory -Force -Path $classes | Out-Null
javac -encoding UTF-8 -d $classes (Get-ChildItem -Recurse -Filter *.java (Join-Path $root "src") | ForEach-Object { $_.FullName })
jar --create --file $jar -C $classes .
mvn install:install-file `
  "-Dfile=$jar" `
  "-DgroupId=com.huawei.bsp" `
  "-DartifactId=com.huawei.bsp.commonlib.resetclient" `
  "-Dversion=25.590.54" `
  "-Dpackaging=jar"
