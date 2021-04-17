plugins { id("io.vacco.oss.gitflow") version "0.9.7" }

group = "io.vacco.jsonbeans"
version = "1.0.0"

configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(true, true)
}

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
