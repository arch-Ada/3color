plugins { java }
val jmh by sourceSets.creating
dependencies {
    "jmhImplementation"(project(":core"))
    "jmhImplementation"("org.openjdk.jmh:jmh-core:1.37")
    "jmhAnnotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
tasks.register<JavaExec>("jmh") {
    dependsOn(tasks.named("jmhClasses"))
    classpath = jmh.runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    args((findProperty("jmhArgs")?.toString() ?: "-wi 2 -i 3 -f 1").split(" "))
}
