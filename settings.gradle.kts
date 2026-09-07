// Root plugin plus isolated compatibility tooling. Real-Paper tests and the
// protocol client are intentionally separate -p builds with their own classpaths.
rootProject.name = "OnlyDragons"

include("lab-tools")
project(":lab-tools").projectDir = file("dev/lab-tools")
