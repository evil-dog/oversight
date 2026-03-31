# Release Process

## Normal Release

1. **Ensure `main` is in a good state** — CI passes, app works on device.

2. **Tag the release** from `main`:
   ```bash
   git checkout main
   git pull origin main
   git tag v1.2.3
   git push origin v1.2.3
   ```

3. **GitHub Actions does the rest** — the `release.yml` workflow triggers automatically, builds a signed APK, and publishes it to the [Releases](../../releases) page as `oversight-v1.2.3.apk`.

> Version numbers follow [semver](https://semver.org/): `MAJOR.MINOR.PATCH`
> - **PATCH** — bug fixes, minor tweaks
> - **MINOR** — new features, API additions
> - **MAJOR** — breaking API changes

---

## Dependency Update Release (Monthly)

On the 1st of each month, the `deps-update.yml` workflow runs automatically and:
1. Checks for stable dependency and Gradle wrapper updates
2. Creates a `deps/YYYY-MM` branch with the updates applied
3. Does a test build and emails the result

### If the build succeeded

The branch is ready to release. Check out the branch, verify on device if desired, then tag:

```bash
git fetch origin
git checkout deps/2026-03
git tag v1.2.3
git push origin deps/2026-03 v1.2.3
```

After releasing, merge back to `main`:
```bash
git checkout main
git merge deps/2026-03
git push origin main
```

### If the build failed

The `deps/YYYY-MM` branch is still created with the dependency updates committed — only the build verification failed. Fix the issues manually:

```bash
# 1. Check out the branch
git fetch origin
git checkout deps/2026-03

# 2. Fix the build — common causes:
#    - Gradle wrapper version too old for updated AGP
#      → update gradle/wrapper/gradle-wrapper.properties distributionUrl
#    - Deprecated API usage broken by a library update
#      → update affected source files in app/src/
#    - Kotlin/Compose compiler compatibility issue
#      → may need to align kotlin and compose plugin versions

# 3. Build locally to verify the fix
docker run --rm \
  -v $(pwd):/project \
  -v /home/evildog/.android-docker:/root/.android \
  -v /home/evildog/.android-docker/debug.keystore:/opt/android-sdk/.android/debug.keystore \
  -v oversight-gradle-cache:/root/.gradle \
  mingc/android-build-box \
  bash -c 'cd /project && ./gradlew assembleDebug'

# 4. Commit the fix
git add <changed files>
git commit -m "fix: resolve build issues after dependency updates"

# 5. Tag and release directly from the branch
git tag v1.2.3
git push origin deps/2026-03 v1.2.3
```

The `release.yml` workflow triggers on the tag regardless of which branch it's on.

After releasing, merge back to `main`:
```bash
git checkout main
git merge deps/2026-03
git push origin main
```

### If you want to skip a month's updates

Simply don't tag the `deps/YYYY-MM` branch. It will sit there harmlessly until deleted:
```bash
git push origin --delete deps/2026-03
```

---

## GitHub Secrets Reference

| Secret | Used by | Purpose |
|--------|---------|---------|
| `SIGNING_KEYSTORE_B64` | `release.yml` | Base64-encoded release keystore |
| `SIGNING_STORE_PASSWORD` | `release.yml` | Keystore password |
| `SIGNING_KEY_ALIAS` | `release.yml` | Key alias (`oversight`) |
| `SIGNING_KEY_PASSWORD` | `release.yml` | Key password |
| `MAIL_USERNAME` | `deps-update.yml` | Gmail address for sending update emails |
| `MAIL_PASSWORD` | `deps-update.yml` | Gmail App Password |
| `MAIL_TO` | `deps-update.yml` | Recipient address for update emails |

### Regenerating the release keystore

> **Keep the keystore file and its passwords somewhere safe.** If lost, you cannot update an existing installation — users would need to uninstall and reinstall.

```bash
keytool -genkeypair -v -keystore release.keystore -alias oversight \
  -keyalg RSA -keysize 2048 -validity 36500 \
  -dname "CN=OverSight, OU=, O=bergnet.us, L=, S=, C=US"

# Encode for the GitHub secret
base64 -w0 release.keystore
```

### Regenerating a Gmail App Password

Google Account → Security → 2-Step Verification → App Passwords.
Generate one named "OverSight CI" and update the `MAIL_PASSWORD` secret.

---

## Triggering the Dependency Check Manually

Go to **Actions → Monthly Dependency Update → Run workflow** to trigger outside of the normal monthly schedule — useful after adding new dependencies or to test the email flow.
