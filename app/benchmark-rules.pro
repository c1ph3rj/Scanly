# Extra rules for the "benchmark" build type only (see baselineprofile/README.md).
# A baseline profile lists classes and methods by name, so the build it is recorded from must
# keep the original names. R8 still shrinks and optimises as it does for a release.
-dontobfuscate
