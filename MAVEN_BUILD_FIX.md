# Maven Build Freeze Fix

## Problem
Maven freezes when trying to download SNAPSHOT dependencies from `https://repo.opencollab.dev/main/`

## Solutions Applied

### 1. Added Maven Connection Timeouts
Created `.mvn/maven.config` with:
```
-Dmaven.wagon.http.connectionTimeout=10000
-Dmaven.wagon.http.readTimeout=20000
-Dmaven.wagon.httpconnectionManager.ttlSeconds=30
```

### 2. Updated Repository Configuration
Modified `pom.xml` opencollab-snapshots repository:
- Set `updatePolicy` to `daily` (only check once per day)
- Added `checksumPolicy` as `warn` (don't fail on checksum issues)
- Enabled releases in addition to snapshots

### 3. Made Floodgate Optional
Set the `floodgate` dependency as `<optional>true</optional>` so it won't block the build

## How to Build

### Option 1: Build with Timeout Protection (Recommended)
```powershell
.\build-with-timeout.ps1
```
This script will:
- Start the Maven build with connection timeouts
- Monitor the process
- Kill it automatically if it freezes for more than 2 minutes
- Provide helpful error messages

### Option 2: Build in Offline Mode (If dependencies are cached)
```bash
mvn clean package -DskipTests -o
```
This uses only locally cached dependencies (won't download anything)

### Option 3: Standard Build with Manual Timeout
```bash
mvn clean package -DskipTests -Dmaven.wagon.http.connectionTimeout=10000 -Dmaven.wagon.http.readTimeout=15000
```
Press Ctrl+C if it freezes

### Option 4: Skip Problematic Dependencies
If Floodgate is not essential, you can comment it out in pom.xml temporarily

## If Build Still Freezes

### Clear Snapshot Cache
```powershell
# Delete cached floodgate snapshots
Remove-Item -Path "$env:USERPROFILE\.m2\repository\org\geysermc" -Recurse -Force
```

### Disable opencollab-snapshots Repository
In `pom.xml`, comment out the entire opencollab-snapshots repository:
```xml
<!--
<repository>
    <id>opencollab-snapshots</id>
    <url>https://repo.opencollab.dev/main/</url>
    ...
</repository>
-->
```

### Use a Different Floodgate Version
Try changing the version from `2.2.5-SNAPSHOT` to a stable release like `2.2.3` or `2.2.4`

## Verify Build Success
After building, check for the JAR file:
```powershell
Get-Item "target\SmpPlugin-*.jar"
```

Should show: `target\SmpPlugin-1.4.jar`

## Additional Maven Commands

### Check if Maven is working
```bash
mvn --version
```

### Validate POM without building
```bash
mvn validate
```

### Download all dependencies (test connectivity)
```bash
mvn dependency:go-offline -B
```

### Show dependency tree
```bash
mvn dependency:tree
```

## Troubleshooting

### Maven completely unresponsive?
1. Check network connectivity: `Test-NetConnection repo.opencollab.dev -Port 443`
2. Try using a VPN or different network
3. Check firewall/antivirus settings
4. Use offline mode: `-o` flag

### Still having issues?
Contact me with:
- Output of `mvn -X clean compile -DskipTests` (debug mode)
- Your Maven version: `mvn --version`
- Network status: `Test-NetConnection repo.opencollab.dev -Port 443`
