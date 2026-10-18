# Building FPSForge

The `fpsforge-build` branch contains the Fabric 1.21.11 client mod source.

GitHub Actions builds the remapped JAR using Java 21 and Gradle 9.6. On success, open the repository's Actions tab, select the **Build FPSForge JAR** workflow run, and download the **FPSForge-JARs** artifact.

The resulting mod should be used only with a matching Fabric Loader/Fabric API Minecraft 1.21.11 instance. It has not yet been tested in a live game until the workflow build succeeds and gameplay checks are performed.
