import org.gradle.api.tasks.SourceSetContainer

plugins { java }

val sdkDirectory = providers.gradleProperty("intellijLocalPath")
    .orElse(rootProject.layout.projectDirectory.dir("build/sdk/community-2025.2.6.3").asFile.absolutePath)
val jbrDirectory = providers.gradleProperty("pvfRuntimePath")
    .orElse(rootProject.layout.projectDirectory.dir("build/sdk/jbr-21.0.9-b1038.78").asFile.absolutePath)
val productDirectory = layout.dir(providers.provider {
    providers.gradleProperty("standaloneOutputDir").orNull?.let { rootProject.file(it) }
        ?: layout.buildDirectory.dir("product/PVFStudio-${project.version}").get().asFile
})

dependencies {
    implementation(project(":pvf-core"))
    implementation(project(":pvf-workspace"))
    compileOnly(files(sdkDirectory.map { fileTree("$it/lib") { include("**/*.jar") } }))
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
tasks.jar {
    archiveFileName.set("pvf-studio.jar")
    manifest { attributes("Main-Class" to "studio.pvf.product.PvfStudioMain", "Implementation-Version" to project.version) }
    for (module in listOf(":pvf-core", ":pvf-workspace")) {
        from(project(module).extensions.getByType<SourceSetContainer>().named("main").map { it.output })
    }
}

val assembler = sourceSets.create("assembler")
dependencies.add(assembler.compileOnlyConfigurationName, files(sdkDirectory.map {
    listOf(file("$it/lib/platform-loader.jar"), file("$it/lib/annotations.jar"))
}))
tasks.named<JavaCompile>(assembler.compileJavaTaskName) { options.release.set(21) }
val generatedProductResources = layout.buildDirectory.dir("generated/product-resources")
val generateProductResources = tasks.register<JavaExec>("generateProductResources") {
    dependsOn(tasks.named(assembler.classesTaskName))
    classpath = assembler.output
    mainClass.set("studio.pvf.build.ProductAssembler")
    inputs.file(sdkDirectory.map { "$it/lib/app.jar" })
    inputs.file(sdkDirectory.map { "$it/lib/app-client.jar" })
    outputs.dir(generatedProductResources)
    args("--generate-resources", sdkDirectory.get(), generatedProductResources.get().asFile.absolutePath)
}
sourceSets.main { resources.srcDir(generatedProductResources) }
tasks.processResources {
    dependsOn(generateProductResources)
    inputs.property("productVersion", project.version.toString())
    val versionParts = project.version.toString().split('.')
    filesMatching("idea/PvfStudioApplicationInfo.xml") {
        filter { line -> line.replace("@MAJOR@", versionParts[0]).replace("@MINOR@", versionParts[1]).replace("@MICRO@", versionParts[2]) }
    }
}

val nativeDirectory = layout.buildDirectory.dir("native-launcher")
val nativeLauncher = tasks.register<Exec>("buildWindowsLauncher") {
    inputs.dir(rootProject.file("tools/windows/launcher"))
    inputs.file(rootProject.file("tools/build-launcher.ps1"))
    inputs.property("version", project.version.toString())
    outputs.file(nativeDirectory.map { it.file("PVFStudio.exe") })
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        rootProject.file("tools/build-launcher.ps1").absolutePath,
        "-OutputDirectory", nativeDirectory.get().asFile.absolutePath, "-Version", project.version.toString())
}

val assembleProduct = tasks.register<JavaExec>("assembleProduct") {
    group = "distribution"
    description = "Build PVF Studio from its platform module dependency closure, without an IDEA host or bundled plugins."
    dependsOn(tasks.jar, tasks.named(assembler.classesTaskName), nativeLauncher)
    classpath = assembler.output + files(sdkDirectory.map { listOf(file("$it/lib/platform-loader.jar"), file("$it/lib/annotations.jar")) })
    mainClass.set("studio.pvf.build.ProductAssembler")
    inputs.file("platform-roots.txt")
    inputs.file("platform.lock.json")
    inputs.file(tasks.jar.flatMap { it.archiveFile })
    inputs.file(rootProject.file("LICENSE"))
    inputs.dir(jbrDirectory)
    inputs.file(sdkDirectory.map { "$it/product-info.json" })
    inputs.file(sdkDirectory.map { "$it/modules/module-descriptors.dat" })
    inputs.dir(sdkDirectory.map { "$it/lib" })
    inputs.dir(sdkDirectory.map { "$it/license" })
    outputs.dir(productDirectory)
    outputs.file(layout.buildDirectory.file("product-location.txt"))
    args(sdkDirectory.get(), jbrDirectory.get(), tasks.jar.get().archiveFile.get().asFile.absolutePath,
        productDirectory.get().asFile.absolutePath, project.version.toString(), file("platform-roots.txt").absolutePath,
        nativeDirectory.get().file("PVFStudio.exe").asFile.absolutePath, rootProject.file("LICENSE").absolutePath)
}
val verifyProduct = tasks.register<Exec>("verifyProduct") {
    group = "verification"
    description = "Verify product identity, core integration, settings and zero bundled plugins without GUI dialogs."
    dependsOn(assembleProduct)
    val app = productDirectory.get().asFile.resolve("app")
    val validation = layout.buildDirectory.dir("product-check").get().asFile
    doFirst { validation.resolve("config").mkdirs() }
    commandLine(app.resolve("jbr/bin/java.exe").absolutePath,
        "@${app.resolve("bin/pvf-studio.vmoptions").absolutePath}",
        "-Xbootclasspath/a:${app.resolve("lib/nio-fs.jar").absolutePath}",
        "-Dpvf.studio.home=${app.absolutePath}",
        "-Didea.config.path=${validation.resolve("config").absolutePath}",
        "-Didea.system.path=${validation.resolve("system").absolutePath}",
        "-Didea.log.path=${validation.resolve("log").absolutePath}",
        "-Didea.plugins.path=${validation.resolve("plugins").absolutePath}",
        "-Djava.awt.headless=true", "-Didea.is.integration.test=true",
        "-cp", "${app.resolve("lib/platform-loader.jar").absolutePath};${app.resolve("lib/pvf-studio.jar").absolutePath}",
        "studio.pvf.product.PvfStudioMain", "--check", validation.resolve("result.properties").absolutePath)
}
tasks.register<Exec>("runProduct") {
    group = "application"
    description = "Run the independent PVF Studio product, without an IDEA host or plugin sandbox."
    dependsOn(verifyProduct)
    commandLine(productDirectory.get().file("PVFStudio.exe").asFile.absolutePath)
    providers.gradleProperty("pvfPath").orNull?.let { args(it) }
}
tasks.register<Zip>("productZip") {
    group = "distribution"
    dependsOn(verifyProduct)
    archiveFileName.set("PVFStudio-${project.version}-windows-x64-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(productDirectory) { into("PVFStudio") }
    isZip64 = true
}
