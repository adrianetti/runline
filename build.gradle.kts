buildscript {
    dependencies {
        // AGP's built-in Kotlin accepts a newer compiler via its documented classpath override.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        // Keep the provider, PKIX and utility modules on one patched upstream release.
        classpath(platform("org.bouncycastle:bc-jdk18on-bom:1.86"))
        constraints {
            add("classpath", "com.google.guava:guava:33.7.2-jre") {
                because("Patch the Guava version selected by Android build tools")
            }
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6") {
                because("Patch bundletool's transitive JOSE implementation")
            }
            add("classpath", "org.jdom:jdom2:2.0.6.1") {
                because("Patch Jetifier's transitive XML parser")
            }
            add("classpath", "org.apache.commons:commons-lang3:3.21.0") {
                because("Patch the recursive type parser used by Android build tools")
            }
        }
    }
}
plugins {
    id("com.android.application") version "9.5.0" apply false
}

subprojects {
    configurations.configureEach {
        if (name == "androidLintTool") {
            // Lint has its own isolated tool classpath, independent of the buildscript.
            // Constraints preserve AGP's default lint dependency (a direct BOM would suppress it).
            listOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on").forEach { module ->
                dependencyConstraints.add(project.dependencies.constraints.create("org.bouncycastle:$module:1.86"))
            }
            dependencyConstraints.add(project.dependencies.constraints.create("com.google.guava:guava:33.7.2-jre"))
            dependencyConstraints.add(project.dependencies.constraints.create("org.apache.commons:commons-lang3:3.21.0"))
            dependencyConstraints.add(project.dependencies.constraints.create("org.apache.httpcomponents:httpclient:4.5.14"))
        }
    }
}
