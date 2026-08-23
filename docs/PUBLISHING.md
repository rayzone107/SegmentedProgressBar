# Publishing

This library goes out through two channels from the same build:

| Channel | Coordinates | How a release happens |
| --- | --- | --- |
| Maven Central | `io.github.rayzone107:segmentedprogressbar` | One command from a maintainer's machine |
| JitPack | `com.github.rayzone107.SegmentedProgressBar:segmentedprogressbar` | Automatic, on first request for a tag |
| GitHub release | `SegmentedProgressBar-demo-<version>.apk` | Automatic, when the release is published |

Maven Central is the channel to point consumers at: it needs no custom
repository, and its artifacts are signed and immutable. JitPack stays because it
costs nothing to keep and it is what versions 2.0.0 and 2.1.0 were published
through.

The third row is the demo app, not the library. It is **not on Google Play and
should not be**: the developers who would install it arrive through Maven Central
and the README rather than through store search, Play's Minimum Functionality
policy explicitly targets demo and test builds, and a listing would drag in
annual target API deadlines, a Data Safety declaration and a privacy policy for
an app that exists to show a progress bar. Attaching the APK to the release costs
nothing and expires never.

The two do not interfere. The modules declare `io.github.rayzone107`, the
namespace Central can verify, and JitPack re-serves whatever reaches the local
Maven repository under its own repository-level coordinates: it rewrites both the
artifacts' group and the `segmentedprogressbar-compose` to `segmentedprogressbar`
dependency, so a JitPack consumer never sees the `io.github` group. That was
verified against a real JitPack build of a branch snapshot, not assumed.

---

## Security first

The 2018 `gradle.properties` in this repository's history committed a Nexus
password and a GPG signing passphrase in plain text. **They are in the public git
history and must be treated as compromised.** Two consequences that matter here:

- **Generate a new signing key.** Do not resurrect the old one (`91406D3F`).
- **Never put credentials in this repository**, not even briefly. Everything
  below lives in `~/.gradle/gradle.properties` (outside the repo) or in the
  environment. The build reads them from there and nowhere else.

---

## One-time setup

This part is done once per machine, by a maintainer. It cannot be scripted from
inside the repository, because all of it involves accounts and secrets.

### 1. A Central Portal account and the namespace

1. Sign in at [central.sonatype.com](https://central.sonatype.com) with GitHub.
   (The old OSSRH / `oss.sonatype.org` flow this project used in 2018 no longer
   exists.)
2. Add the namespace `io.github.rayzone107`.
3. The Portal shows a verification code and asks for a public GitHub repository
   named after it, under the `rayzone107` account. Create the empty repository,
   click verify, then delete the repository once the namespace shows as verified.
4. Generate a **user token** (Account, then Generate User Token). It comes as a
   username and password pair, not as the account password.

### 2. A signing key

Maven Central rejects unsigned artifacts, so releases are signed with a GPG key
whose public half is on a keyserver.

```bash
# Generate a key. Use a real name and email; the email is what identifies it.
gpg --full-generate-key          # RSA, 4096 bits, no expiry or a long one

# Note the key id, the long hex string on the "sec" line.
gpg --list-secret-keys --keyid-format LONG

# Publish the public half. keys.openpgp.org is the server Central actually
# consults, so this one is not optional. Sending to keyserver.ubuntu.com as well
# costs nothing and is what other tools tend to look at.
gpg --keyserver hkps://keys.openpgp.org --send-keys <KEY_ID>
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>

# Export the private half, armoured, for the build to sign with.
gpg --armor --export-secret-keys <KEY_ID>
```

**Then verify the email address on the key, or the release will fail.**
keys.openpgp.org strips every user id off an uploaded key until the owner proves
they control the address, and a key with no user id is one GnuPG refuses to
import at all. Central therefore fetches the key, cannot load it, and reports
`Could not find a public key by the key fingerprint` against every single file,
which reads like a signing problem and is not one. Upload the key at
[keys.openpgp.org/upload](https://keys.openpgp.org/upload), which offers to send
a verification mail, and click the link in it.

Proof that it worked, and worth running before a first release, because it is
exactly what Central does:

```bash
export GNUPGHOME=$(mktemp -d)          # an empty keyring, like Central's
curl -s "https://keys.openpgp.org/vks/v1/by-fingerprint/<FINGERPRINT>" | gpg --import
gpg --verify some-artifact.pom.asc some-artifact.pom
```

`no user ID` on import means the address is still unverified. A `Good signature`
means Central will accept the release.

Back the key up somewhere that is not this machine and not this repository. A
lost signing key is not a disaster (generate another and publish it), but a
leaked one is.

### 3. Credentials on this machine

Put them in `~/.gradle/gradle.properties`, which is outside the repository:

```properties
# Central Portal user token, from step 1.4. Not the account password.
mavenCentralUsername=<token username>
mavenCentralPassword=<token password>

# The armoured private key from step 2, on one line, with every newline written
# as a literal \n. This command prints it in exactly that form:
#
#     gpg --armor --export-secret-keys <KEY_ID> | awk '{printf "%s\\n", $0}'
#
# The plugin reads only these three properties; there is no file-path variant.
# signingInMemoryKeyId is needed only when the keyring holds more than one key.
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
signingInMemoryKeyPassword=<the key's passphrase>
```

Signing switches itself on exactly when `signingInMemoryKey` is present, so a
machine without a key can still run `publishToMavenLocal` to inspect what a
release would contain. It cannot accidentally publish an unsigned release to
Central: the Portal rejects those.

### 4. The demo APK's signing key

A separate key from the GPG one above, and used for a different thing: Android
identifies an app by its signing certificate, so every release of the demo APK
has to be signed with the *same* key or nobody can install a new one over the old
one. It lives in GitHub Actions secrets, because
[`release-demo-apk.yml`](../.github/workflows/release-demo-apk.yml) is what signs
with it.

None of the four values below is looked up from anywhere. The alias and the
password are invented at this keyboard, and the other two are the file itself and
that same password again.

```bash
# Generate it. 100 years, because a demo APK that expires is a support ticket for
# no reason. -dname supplies the identity so keytool skips asking for a name, an
# organisation and a city, none of which mean anything for this key.
keytool -genkeypair -v -keystore demo-release.jks \
  -alias demo -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=SegmentedProgressBar Demo, O=rayzone107, C=US"
```

The only thing it asks for is a password, twice:

```
Enter keystore password:      <- invent one here
Re-enter new password:
```

**There is no second, separate key password.** JDK 9 and later default to the
PKCS12 keystore format, which has no per-key password distinct from the store
password, so keytool never asks for one. Guides that show a third prompt
(`Enter key password for <demo>`) predate that change. Gradle still reads two
properties, so the same password goes into both secrets:

```bash
# The file, base64 encoded, because a GitHub secret holds text and not binary.
base64 < demo-release.jks | gh secret set DEMO_KEYSTORE_BASE64

# These three prompt for their value rather than taking it as an argument, which
# keeps the password out of shell history.
gh secret set DEMO_KEYSTORE_PASSWORD   # the password invented above
gh secret set DEMO_KEY_ALIAS           # demo, the word after -alias
gh secret set DEMO_KEY_PASSWORD        # the same password again

gh secret list                         # names and dates only, never values
```

Writing a secret needs **admin** on the repository, so check `gh auth status`
first: a `gh` signed in as some other account fails these with `HTTP 403`, and
the read-only operations it can do give no warning that it will. The same four
values can be typed into [the repository's Actions secrets
page](https://github.com/rayzone107/SegmentedProgressBar/settings/secrets/actions)
instead, which is often less trouble than juggling `gh auth switch`.

Back the keystore up off this machine, and keep it out of the repository:
`.gitignore` already covers `*.jks`, so it cannot be committed by accident.

The certificate that key produces is this one. It is public, since it ships
inside every APK signed with it, and it is the value to compare against when a
release looks wrong:

```
CN=SegmentedProgressBar Demo, O=rayzone107, C=US
SHA-256: c62c257cbb946101f891184383bc6cd7fb3c16c0c680ba2df98557542e6fd23a
```

Reading it back out of any APK, which is what the release workflow does:

```bash
$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1) \
  verify --print-certs SegmentedProgressBar-demo-<version>.apk
```

**Losing this key is not fatal but it is permanent.** Generate a new one, and say
in the release notes that the demo has to be uninstalled before the new build
will install. That is the whole consequence, since nothing else trusts this
certificate: it never touches Play, and it signs nothing that consumers link
against.

`app/build.gradle.kts` falls back to the debug key when these four properties are
absent, which is what lets a developer machine and a pull request still run
`assembleRelease` to check that shrinking works. That fallback must never reach a
release, so the workflow refuses to start without the secrets and then verifies
the certificate on the APK it built.

---

## Releasing a version

### 1. Prepare

- Update `VERSION_NAME` in [`gradle.properties`](../gradle.properties).
- Add the version's entry to [`CHANGELOG.md`](../CHANGELOG.md), dated.
- Regenerate the README's images if any rendering changed:
  `./gradlew :app:testDebugUnitTest --tests '*DocsScreenshotTest*' -Pdocs`
- Run the gate that CI runs:

```bash
./gradlew build :segmented:apiCheck :segmented-compose:apiCheck
```

`apiCheck` failing means the public API surface moved. If that was deliberate,
`./gradlew :segmented:apiDump :segmented-compose:apiDump` and commit the diff;
if it was not, the release is not ready.

### 2. Inspect what will be published

```bash
./gradlew publishToMavenLocal
ls ~/.m2/repository/io/github/rayzone107/segmentedprogressbar/<version>/
```

Each module should have five files: `.aar`, `-sources.jar`, `-javadoc.jar`,
`.pom` and `.module`. Central requires the first four; the sources jar is what
gives consumers KDoc in the IDE, and the javadoc jar is what
[javadoc.io](https://javadoc.io) renders.

### 3. Tag, which publishes to JitPack

```bash
git tag 2.1.0 && git push origin 2.1.0     # no v prefix, matching every other tag
```

JitPack builds the tag on first request, using
[`jitpack.yml`](../jitpack.yml). Trigger and confirm it:

```bash
curl -s "https://jitpack.io/api/builds/com.github.rayzone107.SegmentedProgressBar/<version>"
```

`"ok"` means built. `"Error"` means read the log on the
[build page](https://jitpack.io/#rayzone107/SegmentedProgressBar).

### 4. Publish to Maven Central

```bash
./gradlew publishToMavenCentral --no-configuration-cache
```

This builds both modules, signs every artifact and uploads one bundle, which
then waits in the Portal's Deployments view until Publish is pressed by hand.
`publishAndReleaseToMavenCentral` skips that pause and releases immediately;
prefer the two-step form, since a released version can never be taken back.

**Check the result rather than trusting the build's exit code.** The upload task
prints `Skipping deployment validation!` and succeeds without waiting, so a
bundle that Central rejects still looks like a green build. The deployment id it
prints is what to ask about:

```bash
TOKEN=$(printf '%s:%s' "$mavenCentralUsername" "$mavenCentralPassword" | base64)
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "https://central.sonatype.com/api/v1/publisher/status?id=<DEPLOYMENT_ID>"
```

`VALIDATED` means it is ready for the Publish button, and `errors` lists
anything Central objected to. A failed deployment can be thrown away and redone:

```bash
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" \
  "https://central.sonatype.com/api/v1/publisher/deployment/<DEPLOYMENT_ID>"
```

The artifacts are then searchable in a few minutes and resolvable from
`mavenCentral()` within roughly half an hour. **A released version is
immutable**: the fix for a broken release is another version, never a
replacement.

### 5. Afterwards

- Confirm a consumer can actually resolve it, in a scratch project rather than
  in this one:

```kotlin
repositories { mavenCentral() }
dependencies {
    implementation("io.github.rayzone107:segmentedprogressbar:<version>")
    implementation("io.github.rayzone107:segmentedprogressbar-compose:<version>")
}
```

- Update the install snippets in [`README.md`](../README.md) to the new version.
- Write the GitHub release notes from the changelog entry and publish the
  release. **Publishing it is what builds and attaches the demo APK**, through
  [`release-demo-apk.yml`](../.github/workflows/release-demo-apk.yml). It takes a
  couple of minutes, after which `SegmentedProgressBar-demo-<version>.apk` is on
  the release page:

```bash
gh run watch                                   # or read it in the Actions tab
gh release view <version> --json assets --jq '.assets[].name'
```

The run's log prints the signing certificate's SHA-256. It should be
`c62c257cbb946101f891184383bc6cd7fb3c16c0c680ba2df98557542e6fd23a` on **every**
release; a different one means the key changed and existing installs will refuse
the update.

---

## If something goes wrong

**The Portal rejects the deployment as unsigned.** `signingInMemoryKey` is not
being picked up. Check it is in `~/.gradle/gradle.properties` and not in the
repository's own, and that the build agrees:
`./gradlew :segmented:tasks --group publishing | grep -i sign`.

**The Portal rejects it for a missing POM field.** Central requires name,
description, url, licence, developer and SCM entries. They are set in each
module's `build.gradle.kts`; compare a generated POM under
`~/.m2/repository/io/github/rayzone107/` against the
[Central requirements](https://central.sonatype.org/publish/requirements/).

**A JitPack build fails while Central succeeded.** JitPack builds on its own
machines with its own JDK, pinned in `jitpack.yml`. Read the build log before
changing anything; the failure is usually environmental rather than a defect in
the release.

**The version was published with something wrong in it.** Publish the next
patch version. Central does not allow overwriting or deleting a released
version, and JitPack caches builds per tag.

**The demo APK workflow failed on the missing-secrets check.** The four
`DEMO_*` secrets are not set on the repository; step 4 of the one-time setup
above adds them. The release itself is unaffected, so fix the secrets and re-run
the workflow against the existing tag:

```bash
gh workflow run release-demo-apk.yml -f tag=<version>
```

**It failed on the tag check.** The tag was cut from a commit whose
`VERSION_NAME` says something else, so the APK would have been labelled with a
version nobody released. The library artifacts are fine, since they take their
version from the same property; only the tag is wrong. Move the tag onto the
right commit, or release the next patch version if the tag is already public.

**The APK will not install over an older one.** `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
means the two builds were signed with different keys. Compare the certificate
digest in the two workflow runs. If the key genuinely changed, there is no repair
short of uninstalling the old build; say so in the release notes.
