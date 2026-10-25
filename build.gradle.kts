plugins { base }

allprojects {
    group = "studio.pvf"
    version = "0.11.0"
    repositories { mavenCentral() }
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(17)
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging { events("failed", "skipped") }
        }
    }
}

tasks.named("check") {
    dependsOn(":pvf-core:check", ":pvf-workspace:check", ":pvf-cli:check", ":pvf-preview:check")
}
