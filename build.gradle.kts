plugins {
    application
    // One runnable jar with JDA and the SunLicense SDK inside (see shadowJar below).
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    mavenCentral()
}

dependencies {
    // JDA, the Java Discord API (Apache-2.0). Voice support (opus) left out: this bot doesn't need it.
    implementation("net.dv8tion:JDA:6.7.0") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
    // The official SunLicense Java SDK (MIT, no dependencies): https://github.com/sunlicense-com/sunlicense-java
    implementation("com.sunlicense:sunlicense:0.0.1")
    // Reads SunLicense's JSON answers and offline license files.
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.slf4j:slf4j-simple:2.0.17")   // JDA's log output

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

application {
    mainClass = "com.example.mybot.MyBot"
}

tasks {
    processResources {
        // SunLicense settings from gradle.properties, -P flags or ORG_GRADLE_PROJECT_* environment variables.
        val values = mapOf(
            "sunlicenseUrl" to (project.findProperty("sunlicenseUrl") ?: "https://api.sunlicense.com").toString(),
            "sunlicenseApiKey" to (project.findProperty("sunlicenseApiKey") ?: "").toString(),
            "sunlicensePublicKey" to (project.findProperty("sunlicensePublicKey") ?: "").toString(),
        )
        inputs.properties(values)
        filesMatching("sunlicense.properties") {
            expand(values)
        }
    }

    shadowJar {
        archiveClassifier = ""
        mergeServiceFiles()
    }

    jar {
        archiveClassifier = "plain"
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }

    named<JavaExec>("run") {
        workingDir = layout.projectDirectory.dir("run").asFile
        doFirst { workingDir.mkdirs() }
    }
}
