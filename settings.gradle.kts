rootProject.name = "pvf-studio"
include("pvf-core", "pvf-workspace", "pvf-cli", "pvf-preview")

// Core builds stay small and do not download an IDE distribution.
if (providers.gradleProperty("withIntellij").orNull == "true") {
    include("pvf-product")
}
