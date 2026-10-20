#!/usr/bin/env bash
# Builds the compile-time APIs SmpPlugin needs from their public source code and installs them into
# ~/.m2 under the coordinates pom.xml uses. Only for build environments that can reach Maven Central
# and GitHub but not repo.papermc.io, hub.spigotmc.org or the plugin repositories (CodeMC, JitPack,
# dmulloy2, extendedclip, lunarclient, fancyinnovations, maxhenkel).
#
# The jars it produces are LOCAL VERIFICATION BUILDS, not official artifacts. Never ship or publish
# them. Release builds must use the official repositories in pom.xml.
#
# Usage:  tools/local-api-bootstrap.sh [work-dir]
# Then:   mvn -s tools/central-only-settings.xml clean package
#
# What is built (sources pinned to the versions in pom.xml):
#   io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT   PaperMC/Paper ver/1.21.11, paper-api module
#   com.mojang:brigadier:1.3.10                        Mojang/brigadier master (1.3 line)
#   com.comphenix.protocol:ProtocolLib:5.3.0           dmulloy2/ProtocolLib tag 5.3.0 (the classes SmpPlugin uses)
#   net.skinsrestorer:skinsrestorer-api:15.12.0        SkinsRestorer/SkinsRestorer tag 15.12.0, api module
#   org.geysermc.cumulus:cumulus:1.1.2                 GeyserMC/Cumulus commit 496d557 ("1.1.2 release")
#   me.clip:placeholderapi:2.12.2                      PlaceholderAPI/PlaceholderAPI tag 2.12.2 (expansion API)
#   com.lunarclient:apollo-api:1.2.5                   LunarClient/Apollo tag v1.2.5, api module
#   com.github.MilkBowl:VaultAPI:1.7                   MilkBowl/VaultAPI tag 1.7
#   de.oliver:FancyNpcs:2.9.2                          FancyInnovations/FancyPlugins commit 24d4266, fn-v2-api
#   de.maxhenkel.voicechat:voicechat-api:2.6.24        henkelmax/simple-voice-chat branch 1.21.11, api module
#
# FancyNpcs' API needs two internal types that are not public (a FancyAnalytics logger and a
# ChatColorHandler call inside SkinData). The script compiles them as tiny stand-ins that are kept
# OUT of the FancyNpcs jar; SmpPlugin uses neither.
set -euo pipefail

WORK="${1:-${TMPDIR:-/tmp}/smpplugin-api-bootstrap}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SETTINGS="$REPO_ROOT/tools/central-only-settings.xml"
MVN=(mvn -B -q -s "$SETTINGS"
     -Daether.connector.basic.threads=1
     -Daether.connector.http.retryHandler.count=10
     -Daether.connector.http.retryHandler.interval=6000
     -Daether.connector.http.retryHandler.serviceUnavailable=429,503)
JAVAC=(javac -nowarn -Xlint:none -XDsuppressNotes -encoding UTF-8 --release 21)
mkdir -p "$WORK"/{src,lib,out,jars}
cd "$WORK"

clone() { # url dir ref [sparse paths...]
    local url="$1" dir="$2" ref="$3"; shift 3
    [ -d "src/$dir" ] && return 0
    if [ "$#" -gt 0 ]; then
        GIT_LFS_SKIP_SMUDGE=1 git clone -q --depth 1 --branch "$ref" --filter=blob:none --sparse "$url" "src/$dir"
        git -C "src/$dir" sparse-checkout set "$@"
    else
        GIT_LFS_SKIP_SMUDGE=1 git clone -q --depth 1 --branch "$ref" "$url" "src/$dir"
    fi
}

fetch_deps() { # dir <dependency xml...>
    local dir="$1"; shift
    mkdir -p "deps-$dir"
    cat > "deps-$dir/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>local.verify</groupId><artifactId>$dir</artifactId><version>1</version><packaging>pom</packaging>
<dependencyManagement><dependencies><dependency><groupId>net.kyori</groupId><artifactId>adventure-bom</artifactId>
<version>4.26.1</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
<dependencies>$*</dependencies></project>
EOF
    (cd "deps-$dir" && "${MVN[@]}" org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
        -DoutputDirectory="$WORK/lib/$dir")
}
dep() { printf '<dependency><groupId>%s</groupId><artifactId>%s</artifactId>%s</dependency>' "$1" "$2" "${3:+<version>$3</version>}"; }

jar_dir() { (cd "out/$1" && jar cf "$WORK/jars/$2" .); }

install() { # jar group artifact version description [pom-dependencies-xml]
    cat > "jars/$3.pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>$2</groupId><artifactId>$3</artifactId><version>$4</version>
<description>LOCAL VERIFICATION BUILD from $5. Not an official artifact.</description>${6:-}</project>
EOF
    "${MVN[@]}" org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file -Dfile="jars/$1" -DpomFile="jars/$3.pom"
    echo "installed $2:$3:$4"
}

# ---- Paper API dependencies (versions from paper-api/build.gradle.kts on ver/1.21.11) ----
PAPER_DEPS="$(dep com.google.guava guava 33.3.1-jre)$(dep com.google.code.gson gson 2.11.0)$(dep org.yaml snakeyaml 2.2)\
$(dep org.joml joml 1.10.8)$(dep it.unimi.dsi fastutil 8.5.15)$(dep org.apache.logging.log4j log4j-api 2.24.1)\
$(dep org.slf4j slf4j-api 2.0.16)$(dep net.md-5 bungeecord-chat 1.21-R0.1)$(dep net.kyori adventure-api)\
$(dep net.kyori adventure-text-minimessage)$(dep net.kyori adventure-text-serializer-gson)\
$(dep net.kyori adventure-text-serializer-legacy)$(dep net.kyori adventure-text-serializer-plain)\
$(dep net.kyori adventure-text-logger-slf4j)$(dep org.apache.maven maven-resolver-provider 3.9.6)\
$(dep org.apache.maven.resolver maven-resolver-connector-basic 1.9.18)$(dep org.apache.maven.resolver maven-resolver-transport-http 1.9.18)\
$(dep org.jetbrains annotations 26.0.2)$(dep org.checkerframework checker-qual 3.49.2)$(dep org.jspecify jspecify 1.0.0)"
fetch_deps paper "$PAPER_DEPS"
fetch_deps ext "$(dep org.bstats bstats-bukkit 3.1.0)$(dep net.kyori adventure-platform-bukkit 4.4.1)$(dep net.bytebuddy byte-buddy 1.15.1)\
$(dep com.googlecode.json-simple json-simple 1.1.1)$(dep org.projectlombok lombok 1.18.36)$(dep io.leangen.geantyref geantyref 1.3.16)"
fetch_deps old "$(dep io.netty netty-all 4.0.23.Final)$(dep commons-lang commons-lang 2.6)"

PAPER_CP="$(ls "$WORK"/lib/paper/*.jar | tr '\n' ':')"
# adventure-platform pulls adventure 4.21; keep the Paper API's 4.26 first and drop the older core jars.
EXT_CP="$(ls "$WORK"/lib/ext/*.jar | grep -vE 'adventure-(api|key|text-serializer-(gson|json|legacy|commons))-4\.21' | tr '\n' ':')"
OLD_CP="$(ls "$WORK"/lib/old/*.jar | tr '\n' ':')"
LOMBOK="$(ls "$WORK"/lib/ext/lombok-*.jar)"

# ---- brigadier ----
clone https://github.com/Mojang/brigadier brigadier master
rm -rf out/brigadier && mkdir -p out/brigadier
"${JAVAC[@]}" -d out/brigadier $(find src/brigadier/src/main/java -name '*.java')
jar_dir brigadier brigadier-1.3.10.jar
install brigadier-1.3.10.jar com.mojang brigadier 1.3.10 "github.com/Mojang/brigadier master"

# ---- Paper API ----
clone https://github.com/PaperMC/paper paper ver/1.21.11 paper-api
rm -rf out/paper && mkdir -p out/paper
find src/paper/paper-api/src/main/java src/paper/paper-api/src/generated/java -name '*.java' > out/paper-sources.txt
"${JAVAC[@]}" -proc:none -J-Xmx3g -cp "$PAPER_CP$WORK/jars/brigadier-1.3.10.jar" -d out/paper @out/paper-sources.txt
[ -d src/paper/paper-api/src/main/resources ] && cp -r src/paper/paper-api/src/main/resources/. out/paper/
jar_dir paper paper-api-1.21.11-R0.1-SNAPSHOT.jar
PAPER_POM_DEPS="<dependencyManagement><dependencies><dependency><groupId>net.kyori</groupId><artifactId>adventure-bom</artifactId>\
<version>4.26.1</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>\
<dependencies>$PAPER_DEPS$(dep com.mojang brigadier 1.3.10)</dependencies>"
install paper-api-1.21.11-R0.1-SNAPSHOT.jar io.papermc.paper paper-api 1.21.11-R0.1-SNAPSHOT \
    "github.com/PaperMC/Paper ver/1.21.11 paper-api" "$PAPER_POM_DEPS"

API_CP="$PAPER_CP$WORK/jars/brigadier-1.3.10.jar:$WORK/jars/paper-api-1.21.11-R0.1-SNAPSHOT.jar"

compile_tree() { # name jar srcdir [extra-cp] [processorpath]
    rm -rf "out/$1" && mkdir -p "out/$1"
    find "$3" -name '*.java' > "out/$1-sources.txt"
    if [ -n "${5:-}" ]; then
        "${JAVAC[@]}" -processorpath "$5" -cp "$API_CP:$EXT_CP${4:-}" -d "out/$1" @"out/$1-sources.txt"
    else
        "${JAVAC[@]}" -proc:none -cp "$API_CP:$EXT_CP${4:-}" -d "out/$1" @"out/$1-sources.txt"
    fi
    jar_dir "$1" "$2"
}
compile_roots() { # name jar sourcepath extra-cp roots...
    local name="$1" jar="$2" sp="$3" cp="$4"; shift 4
    rm -rf "out/$name" && mkdir -p "out/$name"
    # Emit every class that compiles; upstream-only classes built against other server APIs are skipped.
    "${JAVAC[@]}" -proc:none -implicit:class -XDshould-stop.ifError=GENERATE -cp "$cp" -sourcepath "$sp" \
        -d "out/$name" "$@" || true
    jar_dir "$name" "$jar"
}

# ---- VaultAPI, voicechat-api, Cumulus, SkinsRestorer, Apollo ----
clone https://github.com/MilkBowl/VaultAPI vaultapi 1.7
compile_tree vault VaultAPI-1.7.jar src/vaultapi/src/main/java
install VaultAPI-1.7.jar com.github.MilkBowl VaultAPI 1.7 "github.com/MilkBowl/VaultAPI tag 1.7"

clone https://github.com/henkelmax/simple-voice-chat voicechat 1.21.11 api
compile_tree voicechat voicechat-api-2.6.24.jar src/voicechat/api/src/main/java
install voicechat-api-2.6.24.jar de.maxhenkel.voicechat voicechat-api 2.6.24 "github.com/henkelmax/simple-voice-chat 1.21.11 api"

if [ ! -d src/cumulus ]; then
    GIT_LFS_SKIP_SMUDGE=1 git clone -q https://github.com/GeyserMC/Cumulus src/cumulus
    git -C src/cumulus checkout -q 496d557
fi
compile_tree cumulus cumulus-1.1.2.jar src/cumulus/src/main/java
install cumulus-1.1.2.jar org.geysermc.cumulus cumulus 1.1.2 "github.com/GeyserMC/Cumulus 496d557"

clone https://github.com/SkinsRestorer/SkinsRestorer skinsrestorer 15.12.0 api shared
compile_tree skinsrestorer skinsrestorer-api-15.12.0.jar src/skinsrestorer/api/src/main/java "" "$LOMBOK"
install skinsrestorer-api-15.12.0.jar net.skinsrestorer skinsrestorer-api 15.12.0 "github.com/SkinsRestorer/SkinsRestorer 15.12.0 api"

clone https://github.com/LunarClient/Apollo apollo v1.2.5 api common extra
rm -rf out/apollo && mkdir -p out/apollo
{ find src/apollo/api/src/main/java -name '*.java'; find src/apollo/extra -path '*adventure4*' -path '*src/main/java*' -name '*.java'; } > out/apollo-sources.txt
"${JAVAC[@]}" -processorpath "$LOMBOK" -cp "$API_CP:$EXT_CP" -d out/apollo @out/apollo-sources.txt
jar_dir apollo apollo-api-1.2.5.jar
install apollo-api-1.2.5.jar com.lunarclient apollo-api 1.2.5 "github.com/LunarClient/Apollo v1.2.5 api"

# ---- PlaceholderAPI (expansion API closure) ----
clone https://github.com/PlaceholderAPI/PlaceholderAPI placeholderapi 2.12.2
PA=src/placeholderapi/src/main/java/me/clip/placeholderapi
compile_roots papi placeholderapi-2.12.2.jar src/placeholderapi/src/main/java "$API_CP:$EXT_CP" \
    "$PA/expansion/PlaceholderExpansion.java" "$PA/PlaceholderAPI.java"
install placeholderapi-2.12.2.jar me.clip placeholderapi 2.12.2 "github.com/PlaceholderAPI/PlaceholderAPI 2.12.2"

# ---- ProtocolLib (the API classes SmpPlugin imports, plus DataFixerUpper utils they reference) ----
clone https://github.com/Mojang/DataFixerUpper dfu master
DFU=src/dfu/src/main/java/com/mojang/datafixers/util
rm -rf out/dfu && mkdir -p out/dfu
"${JAVAC[@]}" -proc:none -implicit:class -cp "$PAPER_CP" -sourcepath src/dfu/src/main/java -d out/dfu "$DFU/Either.java" "$DFU/Pair.java"
jar_dir dfu dfu-util.jar
clone https://github.com/dmulloy2/ProtocolLib protocollib 5.3.0
PL=src/protocollib/src/main/java/com/comphenix/protocol
PL_ROOTS=("$PL/ProtocolLibrary.java" "$PL/ProtocolManager.java" "$PL/PacketType.java" "$PL/reflect/StructureModifier.java")
for c in PacketAdapter PacketEvent PacketContainer ListenerPriority PacketListener; do PL_ROOTS+=("$PL/events/$c.java"); done
for c in WrappedGameProfile PlayerInfoData EnumWrappers WrappedSignedProperty WrappedTeamParameters \
         WrappedRemoteChatSessionData WrappedParticle WrappedEnumEntityUseAction WrappedDataWatcher \
         WrappedDataValue WrappedChatComponent; do PL_ROOTS+=("$PL/wrappers/$c.java"); done
compile_roots protocollib ProtocolLib-5.3.0.jar src/protocollib/src/main/java \
    "$OLD_CP$API_CP:$EXT_CP:$WORK/jars/dfu-util.jar" "${PL_ROOTS[@]}"
install ProtocolLib-5.3.0.jar com.comphenix.protocol ProtocolLib 5.3.0 "github.com/dmulloy2/ProtocolLib 5.3.0"

# ---- FancyNpcs 2.9.2 (Npc and NpcInteractEvent closure) ----
if [ ! -d src/fancynpcs ]; then
    GIT_LFS_SKIP_SMUDGE=1 git clone -q --filter=blob:none --no-checkout https://github.com/FancyInnovations/FancyPlugins src/fancyplugins
    mkdir -p src/fancynpcs
    git -C src/fancyplugins archive 24d4266 plugins/fancynpcs-v2/fn-v2-api libraries/common libraries/config | tar -x -C src/fancynpcs
fi
mkdir -p out/fn-shims-src/de/oliver/fancyanalytics/logger out/fn-shims-src/org/lushplugins/chatcolorhandler/paper out/fn-shims
cat > out/fn-shims-src/de/oliver/fancyanalytics/logger/ExtendedFancyLogger.java <<'EOF'
package de.oliver.fancyanalytics.logger;
/** Verification-only stand-in for an unpublished FancyNpcs dependency; never shipped. */
public class ExtendedFancyLogger { public ExtendedFancyLogger(String name) { } }
EOF
cat > out/fn-shims-src/org/lushplugins/chatcolorhandler/paper/PaperColor.java <<'EOF'
package org.lushplugins.chatcolorhandler.paper;
/** Verification-only stand-in for a FancyNpcs-internal dependency; never shipped. */
public final class PaperColor {
    public static Handler handler() { return new Handler(); }
    public static final class Handler { public String translateRaw(String s) { return s; } }
}
EOF
"${JAVAC[@]}" -d out/fn-shims $(find out/fn-shims-src -name '*.java')
FN=src/fancynpcs/plugins/fancynpcs-v2/fn-v2-api/src/main/java
compile_roots fancynpcs FancyNpcs-2.9.2.jar \
    "$FN:src/fancynpcs/libraries/common/src/main/java:src/fancynpcs/libraries/config/src/main/java" \
    "$API_CP:$EXT_CP:$WORK/out/fn-shims" "$FN/de/oliver/fancynpcs/api/Npc.java" "$FN/de/oliver/fancynpcs/api/events/NpcInteractEvent.java"
install FancyNpcs-2.9.2.jar de.oliver FancyNpcs 2.9.2 "github.com/FancyInnovations/FancyPlugins 24d4266 fn-v2-api"

echo "Done. Build with: mvn -s tools/central-only-settings.xml clean package"
