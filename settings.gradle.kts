rootProject.name = "pvf-studio"
include("pvf-core", "pvf-workspace", "pvf-cli")

// Core builds stay small and do not download an IDE distribution.
if (providers.gradleProperty("withIntellij").orNull == "true") {
    include("intellij-workbench")
}
