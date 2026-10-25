plugins { application }

dependencies {
    implementation(project(":pvf-workspace"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("studio.pvf.cli.Main")
    applicationName = "pvf-studio"
}

tasks.jar {
    manifest { attributes("Implementation-Version" to project.version) }
}
