# Publishing to F-Droid

This document covers how to get Brightness Widget listed on F-Droid and F-Droid-compatible repositories.

---

## Option 1: IzzyOnDroid Repository (Recommended First Step)

[IzzyOnDroid](https://apt.izzysoft.de/fdroid/) is a third-party F-Droid-compatible repository. It's the fastest way to get listed because it uses your **pre-built signed APKs from GitHub Releases** rather than building from source.

### Prerequisites

- At least one GitHub Release with a signed APK attached (see [RELEASING.md](RELEASING.md))

### Steps

1. Go to the [IzzyOnDroid inclusion request tracker](https://gitlab.com/IzzyOnDroid/repo/-/issues)
2. Open a new issue with:
   - **Title**: `Add us.packden.brightnesswidget (Brightness Widget)`
   - **Body**:
     ```
     App name: Brightness Widget
     Package name: us.packden.brightnesswidget
     Source code: https://github.com/crueber/android-brightness
     License: MIT
     Description: A minimal home screen widget for controlling screen brightness.
     Tap any segment of the horizontal bar to set brightness to that level.
     ```
3. Wait for review (typically 1–3 days)

### What users do

Once accepted, users add the IzzyOnDroid repo to their F-Droid client:

1. Open F-Droid > Settings > Repositories > Add
2. Enter: `https://apt.izzysoft.de/fdroid/repo`
3. Search for "Brightness Widget"

IzzyOnDroid automatically picks up new GitHub Releases.

---

## Option 2: Official F-Droid Repository

The [official F-Droid repository](https://f-droid.org) builds apps **from source** on their own build servers using their own signing keys. This gives maximum trust and reach but takes longer to set up and review.

### Prerequisites

- App must be fully open source (MIT license — done)
- No proprietary dependencies (AndroidX/Jetpack only — done)
- Reproducible builds from source
- At least one tagged release in the git repository

### Steps

1. **Fork the F-Droid Data repository**

   Go to https://gitlab.com/fdroid/fdroiddata and fork it to your GitLab account.

2. **Clone your fork**

   ```bash
   git clone https://gitlab.com/YOUR_USERNAME/fdroiddata.git
   cd fdroiddata
   ```

3. **Create the metadata file**

   Create `metadata/us.packden.brightnesswidget.yml` with the following content:

   ```yaml
   Categories:
     - System
   License: MIT
   AuthorName: Christopher Rueber
   AuthorWebSite: https://github.com/crueber
   SourceCode: https://github.com/crueber/android-brightness
   IssueTracker: https://github.com/crueber/android-brightness/issues

   AutoName: Brightness Widget

   Summary: Tap-zone widget for quick screen brightness control

   Description: |
     A minimal home screen widget for controlling screen brightness.

     Features:
     * Horizontal tap-zone bar — tap any segment to set brightness
     * 10 brightness steps with gamma-corrected mapping that matches
       the Quick Settings slider
     * Auto-brightness is disabled automatically on tap
     * Subtle brightness icon and percentage labels for clarity
     * Resizable horizontally to any width

     Requires the "Modify System Settings" permission, which is granted
     through a simple one-time setup screen in the app.

   RepoType: git
   Repo: https://github.com/crueber/android-brightness.git

   Builds:
     - versionName: 1.0.0
       versionCode: 1
       commit: v1.0.0
       subdir: app
       gradle:
         - yes

   AutoUpdateMode: Version
   UpdateCheckMode: Tags
   CurrentVersion: 1.0.0
   CurrentVersionCode: 1
   ```

4. **Validate the metadata** (optional but recommended)

   Install `fdroidserver`:
   ```bash
   pip install fdroidserver
   ```

   Then lint your metadata:
   ```bash
   fdroid lint -f metadata/us.packden.brightnesswidget.yml
   ```

5. **Commit and push**

   ```bash
   git add metadata/us.packden.brightnesswidget.yml
   git commit -m "Add us.packden.brightnesswidget (Brightness Widget)"
   git push origin master
   ```

6. **Submit a Merge Request**

   Go to your fork on GitLab and create a Merge Request against `https://gitlab.com/fdroid/fdroiddata`.

   In the MR description, include:
   - Link to the source code: https://github.com/crueber/android-brightness
   - License: MIT
   - Brief description of the app

7. **Wait for review**

   F-Droid maintainers will review the MR, test the build, and merge it. This typically takes **1–4 weeks** depending on their queue.

### Updating for new releases

Once accepted, F-Droid automatically picks up new versions thanks to `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`. When you push a new tag (e.g., `v1.1.0`), F-Droid's bot will:

1. Detect the new tag
2. Add a new `Builds` entry to the metadata
3. Build and sign the new version
4. Publish it to the F-Droid repository

You don't need to submit another MR for version updates.

### Important notes for F-Droid

- **F-Droid uses its own signing key**, not yours. Users who install from F-Droid cannot switch to your GitHub Releases APK (or vice versa) without uninstalling first, because the signatures differ.
- **No Google Play Services dependencies** — this app has none, so it's fine.
- **Reproducible builds** are preferred but not strictly required. If F-Droid can build your app from the tagged source, that's sufficient.
- **Anti-features**: F-Droid flags apps with certain behaviors. This app has no ads, no tracking, no non-free dependencies, so no anti-features apply.

---

## Option 3: Both

You can (and should) submit to both IzzyOnDroid and the official F-Droid repo:

- **IzzyOnDroid** gets you listed quickly (days) using your GitHub Releases APKs
- **Official F-Droid** gives you maximum reach and trust (weeks) with source-built APKs

They don't conflict — users will see the app in whichever repos they have enabled. Once the official F-Droid listing is live, users on IzzyOnDroid can switch to the official repo if they prefer (requires reinstall due to different signing keys).

---

## Recommended order

1. Create your first GitHub Release with a signed APK (see [RELEASING.md](RELEASING.md))
2. Submit to IzzyOnDroid (quick win, 1–3 days)
3. Submit to official F-Droid (longer process, 1–4 weeks)
