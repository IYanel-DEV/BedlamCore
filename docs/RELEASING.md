# Releasing BedlamCore

Use one version, one commit, one tag, and one tested jar. Never create several release tags for the same source commit.

1. Set the version once in `build.gradle.kts`.
2. Run `./gradlew --no-daemon clean check build`.
3. Record the SHA-256 of `build/libs/BedlamCore-<version>.jar`.
4. Copy that exact jar to the Paper 1.8.8 server and verify enable, setup, join, game start, death/respawn, `/leave`, reset, and shutdown.
5. Stop 1.8.8, copy the same jar to Paper 26.2, and repeat the checks. Run the servers sequentially.
6. Confirm both logs show the expected BedlamCore version and no errors.
7. Commit the tested source as the configured human author, tag that commit `v<version>`, and push once.
8. Create one GitHub release for that tag, attach the tested jar, include its SHA-256, and describe only changes present in the tagged commit.

If any source or resource changes after step 2, restart from step 2. Compilation or an older server log is not release proof.
