# Publishing

This library goes out through two channels from the same build:

| Channel | Coordinates | How a release happens |
| --- | --- | --- |
| Maven Central | `io.github.rayzone107:segmentedprogressbar` | One command from a maintainer's machine |
| JitPack | `com.github.rayzone107.SegmentedProgressBar:segmentedprogressbar` | Automatic, on first request for a tag |

Maven Central is the channel to point consumers at: it needs no custom
repository, and its artifacts are signed and immutable. JitPack stays because it
costs nothing to keep and it is what versions 2.0.0 and 2.1.0 were published
through.

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
- Write the GitHub release notes from the changelog entry.

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
