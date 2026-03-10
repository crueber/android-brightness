# Releasing Brightness Widget

## One-Time Setup: Create a Signing Keystore

Run this once and keep the `.jks` file somewhere safe (password manager, etc). **Never commit it.**

```bash
keytool -genkey -v \
  -keystore brightness-widget.jks \
  -alias brightness-key \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -dname "CN=Brightness Widget, O=packden, C=US"
```

## One-Time Setup: Add GitHub Secrets

Encode the keystore for GitHub:
```bash
openssl base64 < brightness-widget.jks | tr -d '\n'
# Copy the output
```

In the GitHub repo: **Settings > Secrets and variables > Actions**, add:

| Secret name         | Value                                      |
|---------------------|--------------------------------------------|
| `SIGNING_KEY`       | base64-encoded contents of the `.jks` file |
| `KEY_ALIAS`         | `brightness-key`                           |
| `KEY_STORE_PASSWORD`| the password you chose during `keytool`    |
| `KEY_PASSWORD`      | the key password you chose during `keytool`|

## Publishing a New Release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. Commit the version bump
3. Tag and push:

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 1.1.0"
git tag v1.1.0
git push origin main --tags
```

GitHub Actions will build, sign, and publish the APK to GitHub Releases automatically.

## Building a Signed APK Locally

```bash
export KEYSTORE_PATH="/path/to/brightness-widget.jks"
export KEY_STORE_PASSWORD="your-store-password"
export KEY_ALIAS="brightness-key"
export KEY_PASSWORD="your-key-password"

JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  ./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

## Setting Up Obtainium

Once the first release tag is pushed:
1. In Obtainium, tap **+**
2. Enter: `https://github.com/crueber/android-brightness`
3. Obtainium detects GitHub Releases and tracks new APKs automatically
