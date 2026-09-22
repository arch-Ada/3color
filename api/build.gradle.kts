plugins { java; id("org.springframework.boot") }
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.3"))
    implementation(project(":core"))
    implementation("com.zaxxer:HikariCP")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")
    testImplementation(enforcedPlatform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework:spring-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.processResources { from(rootProject.file("web/dist")) { into("static") } }
tasks.bootJar { archiveFileName.set("three-color.jar") }

// Optional local export tooling is excluded from the public checkout and server JAR.
if (file("src/demo").isDirectory) {
    val demo by sourceSets.creating {
        compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
    tasks.register<JavaExec>("exportDemo") {
        description = "Export a certified, static demo collection from PostgreSQL"
        group = "distribution"
        classpath = demo.runtimeClasspath
        mainClass.set("io.threecolor.api.DemoExporter")
        maxHeapSize = "768m"
        args(rootProject.file("web/demo-data").absolutePath)
        providers.gradleProperty("demoSnapshot").orNull?.let { args(rootProject.file(it).absolutePath) }
    }
}
