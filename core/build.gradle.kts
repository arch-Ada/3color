plugins { `java-library` }
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.jqwik:jqwik:1.9.3")
    testImplementation("org.ow2.sat4j:org.ow2.sat4j.core:2.3.6")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
