# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build
#
# Ein einziger Builder genügt: Das Gradle-Node-Plugin lädt seine eigene Node-Version herunter und
# baut die React-Oberfläche in das Jar hinein. Deshalb gibt es hier keine separate Node-Stage.
#
# Der Cache-Mount auf ~/.gradle ist auf dem Pi der entscheidende Unterschied: Beim ersten Bauen
# werden Gradle, Node und alle Abhängigkeiten geladen, bei jedem weiteren nicht mehr.
#
# Tests laufen hier bewusst nicht mit — sie gehören auf den Entwicklungsrechner, nicht auf einen Pi,
# der gerade ein Update ausrollen soll.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /src
COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :backend:bootJar -x test \
    && cp backend/build/libs/mythglass.jar /mythglass.jar

# ---------------------------------------------------------------------------
# Laufzeit
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

COPY --from=build /mythglass.jar /app/mythglass.jar

ENV MYTHGLASS_LIBRARY_PATH=/data/library
ENV MYTHGLASS_CACHE_PATH=/data/cache

# Ohne Angabe nimmt die JVM im Container nur einen Bruchteil des verfügbaren Speichers.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"

# Der Benutzer unten hat keinen Eintrag in /etc/passwd und damit kein Zuhause; ImageIO braucht
# aber ein beschreibbares Temporärverzeichnis.
ENV HOME=/tmp

# Feste UID 1000 statt eines angelegten Benutzers: Das ist auf Raspberry Pi OS der erste Benutzer,
# sodass der eingehängte Cache-Ordner ohne weiteres Zutun beschreibbar ist.
USER 1000:1000

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/mythglass.jar"]
