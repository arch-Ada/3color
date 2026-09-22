plugins {
    base
    id("org.springframework.boot") version "4.0.3" apply false
}
allprojects {
    group = "io.threecolor"
    version = "0.1.0"
    repositories { mavenCentral() }
}
subprojects {
    apply(plugin = "java")
    extensions.configure<JavaPluginExtension> { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
    tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }
    tasks.withType<Test>().configureEach { useJUnitPlatform(); maxHeapSize = "512m" }
}
