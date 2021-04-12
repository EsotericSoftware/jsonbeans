plugins { id("io.vacco.oss.gitflow") version "0.9.3" }

group = "io.vacco.jsonbeans"
version = "1.0.0"

configure<io.vacco.oss.gitflow.GsPluginProfileExtension> {
  sharedLibrary(true, false)
  addClasspathHell()
}

configure<io.vacco.cphell.ChPluginExtension> {
  resourceExclusions.add("module-info.class")
}
