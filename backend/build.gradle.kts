plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "dev.ermer"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val springModulithVersion = "2.1.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("net.coobird:thumbnailator:0.4.21")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // In Spring Boot 4 ist die MockMvc-Unterstützung aus spring-boot-starter-test herausgelöst.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:$springModulithVersion")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootRun {
    // Ohne das startet bootRun im Verzeichnis backend/, und der Vorgabepfad ./data/library aus der
    // application.yaml zeigt auf backend/data/library — also ins Leere. Die Bibliothek liegt im
    // Wurzelverzeichnis des Projekts, genau dort, wo die Anleitung sie erwartet.
    workingDir = rootProject.projectDir
}

// ---------------------------------------------------------------------------
// Frontend
//
// Die React-Oberfläche wird in dieses Jar hineingebaut. Damit gibt es genau ein Artefakt und einen
// Container: kein nginx daneben, keine CORS-Konfiguration und keine Möglichkeit, dass auf dem Pi
// Backend und Oberfläche in unterschiedlichen Ständen laufen.
//
// Das Node-Plugin lädt seine eigene Node-Version herunter. Deshalb kommt der Docker-Build mit einem
// einzigen JDK-Builder aus und braucht keine zusätzliche Node-Stage.
// ---------------------------------------------------------------------------

val frontendDir = rootProject.layout.projectDirectory.dir("frontend")

node {
    version = "24.19.0"
    download = true
    nodeProjectDir = frontendDir
}

val buildFrontend = tasks.register<com.github.gradle.node.npm.task.NpmTask>("buildFrontend") {
    group = "build"
    description = "Baut die React-Oberfläche nach frontend/dist."
    dependsOn(tasks.npmInstall)
    npmCommand.set(listOf("run", "build"))

    inputs.dir(frontendDir.dir("src"))
    inputs.file(frontendDir.file("index.html"))
    inputs.file(frontendDir.file("package.json"))
    inputs.file(frontendDir.file("vite.config.ts"))
    inputs.file(frontendDir.file("tsconfig.json"))
    outputs.dir(frontendDir.dir("dist"))
}

tasks.processResources {
    dependsOn(buildFrontend)
    from(frontendDir.dir("dist")) {
        into("static")
    }
}
