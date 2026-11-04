plugins {
    application
    // One runnable jar with JDA and the SunLicense SDK inside (see shadowJar below).
    id("com.gradleup.shadow") version "9.6.1"
}

// Your product's code in SunLicense. Set it (-PsunlicenseProduct=… or ORG_GRADLE_PROJECT_sunlicenseProduct) for
// release builds: see "Core unlock" at the end of this file.
val sunlicenseProduct = (project.findProperty("sunlicenseProduct") ?: "").toString().trim()

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
            "sunlicenseProduct" to sunlicenseProduct,
        )
        inputs.properties(values)
        filesMatching("sunlicense.properties") {
            expand(values)
        }
    }

    shadowJar {
        archiveClassifier = ""
        mergeServiceFiles()
        // The bot's version, read at runtime for SunLicense analytics.
        manifest { attributes("Implementation-Version" to project.version.toString()) }
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

// ---- Core unlock and tamper reports (https://docs.sunlicense.com/guide/protection) ----
// With -PsunlicenseProduct=<product code> and the SunLicense CLI signed in (or SUNLICENSE_TOKEN set),
// `./gradlew build` also writes build/release/<jar>: the same bot, but the core package is encrypted with
// `sunlicense seal` (only a valid license check unlocks it) and carries the SHA-256 of your other classes, so a
// modified copy is reported. Ship that jar. Without the property, build/libs has a development build with the core
// in plain. Rename both packages here if you rename yours.
val corePackage = "com/example/mybot/core/"
val ownPackage = "com/example/mybot/"
val sealDir = layout.buildDirectory.dir("sunlicense")

val unpackJar = tasks.register<Sync>("unpackJar") {
    from(zipTree(tasks.shadowJar.flatMap { it.archiveFile }))
    into(sealDir.map { it.dir("unpacked") })
}

// The release jar's content without the core.
val plainClasses = tasks.register<Sync>("plainClasses") {
    from(unpackJar) { exclude("$corePackage**") }
    into(sealDir.map { it.dir("plain") })
}

// The classes whose digests go into the seal: your own, as they are in the final jar.
val digestClasses = tasks.register<Sync>("digestClasses") {
    from(plainClasses) { include("$ownPackage**/*.class") }
    into(sealDir.map { it.dir("digest") })
}

val coreJar = tasks.register<Zip>("coreJar") {
    from(unpackJar) { include("$corePackage**") }
    destinationDirectory = sealDir
    archiveFileName = "core.jar"
}

val sealCore = tasks.register<Exec>("sealCore") {
    dependsOn(digestClasses, coreJar)
    val product = sunlicenseProduct   // a local copy: the configuration cache can't keep script references
    val cli = (project.findProperty("sunlicenseCli") ?: "sunlicense").toString()   // or a full path to the CLI
    val digest = sealDir.get().dir("digest").asFile
    val core = sealDir.get().file("core.jar").asFile
    val sealed = sealDir.get().file("core.sealed").asFile
    inputs.dir(digest)
    inputs.file(core)
    outputs.file(sealed)
    // Run from the digest folder, so each digest is named like the class's entry in the jar.
    workingDir(digest)
    commandLine(cli, "seal", core.absolutePath, "--product", product, "--digest", "**/*.class")
    doFirst {
        if (product.isEmpty()) {
            throw GradleException("Set -PsunlicenseProduct=<your product code> to seal the core")
        }
        standardOutput = sealed.outputStream()
    }
    doLast {
        standardOutput.close()
        if (!sealed.readText().trim().startsWith("SLS1.")) {
            throw GradleException("`sunlicense seal` didn't print sealed data; run it by hand to see why")
        }
    }
}

val releaseJar = tasks.register<Zip>("releaseJar") {
    dependsOn(sealCore, plainClasses)
    val plain = sealDir.map { it.dir("plain") }
    from(plain) { include("META-INF/MANIFEST.MF") }   // first, like a normal jar
    from(plain) { exclude("META-INF/MANIFEST.MF") }
    from(sealDir.map { it.file("core.sealed") }) { into("sunlicense") }
    destinationDirectory = layout.buildDirectory.dir("release")
    archiveFileName = tasks.shadowJar.flatMap { it.archiveFileName }
}

if (sunlicenseProduct.isNotEmpty()) {
    tasks.build { dependsOn(releaseJar) }
}
