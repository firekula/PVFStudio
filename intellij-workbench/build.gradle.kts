import org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

repositories {
    intellijPlatform { defaultRepositories() }
}

dependencies {
    implementation(project(":pvf-core"))
    implementation(project(":pvf-workspace"))
    intellijPlatform {
        val localPath = providers.gradleProperty("intellijLocalPath").orNull
        if (localPath != null) local(localPath) else intellijIdeaCommunity("2025.2.6.3")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

intellijPlatform {
    projectName = "pvf-studio-workbench"
    instrumentCode = false
    buildSearchableOptions = false
    pluginConfiguration {
        name = "PVF Studio Workbench"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "252"
            untilBuild = if (providers.gradleProperty("intellijLocalPath").isPresent) "262.*" else "252.*"
        }
    }
}

tasks.runIde {
    val smokeOutput = providers.gradleProperty("smokeOutputDir").orNull
    val archivePath = providers.gradleProperty("pvfPath").orNull
    if (smokeOutput != null) {
        require(archivePath != null) { "-PsmokeOutputDir requires -PpvfPath" }
        args("pvf-studio-smoke", archivePath, smokeOutput)
    } else {
        args("pvf-studio")
        archivePath?.let { args(it) }
    }
}

// An independent prototype with the unchanged Community runtime included.
// This is deliberately separate from buildPlugin, which only creates a plugin ZIP.
val pluginZip = tasks.named<BuildPluginTask>("buildPlugin")
val standaloneDirectory = layout.buildDirectory.dir("standalone/PVFStudio")
tasks.register<Sync>("assembleStandalone") {
    group = "distribution"
    description = "Assemble PVF Studio with its own bundled IntelliJ runtime (Windows prototype)."
    dependsOn(pluginZip)
    doFirst {
        check(intellijPlatform.productInfo.productCode == "IC") {
            "Standalone open-source packaging requires a real IC distribution. " +
                "The selected ${intellijPlatform.productInfo.productCode} SDK is for local development only."
        }
    }
    into(standaloneDirectory)
    from(provider { intellijPlatform.platformPath }) { into("runtime") }
    // The vendor runtime has a precomputed bundled-plugin list; load our plugin
    // explicitly from the product directory rather than modifying that list.
    from(pluginZip.map { zipTree(it.archiveFile) }) { into("plugins") }
    from("launcher")
    doLast {
        check(standaloneDirectory.get().file("runtime/jbr/bin/java.exe").asFile.exists()) {
            "This Windows distribution requires the SDK installer with its bundled JBR."
        }
        check(standaloneDirectory.get().file("runtime/bin/idea.bat").asFile.exists()) {
            "The selected SDK has no Windows idea.bat launcher."
        }
    }
}

tasks.register<Zip>("standaloneZip") {
    group = "distribution"
    dependsOn("assembleStandalone")
    archiveFileName.set("PVFStudio-${project.version}-windows-prototype.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(standaloneDirectory) { into("PVFStudio") }
    isZip64 = true
}
