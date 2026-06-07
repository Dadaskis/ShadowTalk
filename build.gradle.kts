// Top-level build file — plugin versions are declared in settings.gradle.kts

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
