plugins { id("io.vacco.oss") version "1.0.1" }

group = "io.vacco.jsonbeans"
version = "0.9.0"

configure<io.vacco.oss.CbPluginProfileExtension> {
  sharedLibrary(true, true)
  addClasspathHell()
}

configure<io.vacco.cphell.ChPluginExtension> {
  resourceExclusions.add("module-info.class")
}
