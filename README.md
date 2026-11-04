# Discord bot (Java, JDA) with SunLicense

A working, copyable [JDA](https://github.com/discord-jda/JDA) bot with [SunLicense](https://sunlicense.com)
built in: everyone who runs your bot needs a valid license key, leaked keys can be revoked or blocked,
and your customers' bots keep running if SunLicense is ever unreachable.

- Gradle + Shadow: one runnable jar with JDA 6 and the official [Java SDK](https://github.com/sunlicense-com/sunlicense-java), Java 21
- License key from **config.properties** or `LICENSE_KEY`, or built in by **BuiltByBit / Voxel Shop** placeholders
- Clear console messages for every result (expired, revoked, too many machines, blacklisted, …), keys masked
- Re-checked every few hours, so a revoke reaches running bots (the bot logs out)
- **Offline fallback** with a signed license file (Ed25519, verified without network)
- Tests against a stub server, CI on GitHub Actions, and [AGENTS.md](AGENTS.md) for AI coding assistants

## 1. Set up SunLicense

In the [dashboard](https://app.sunlicense.com):

1. **Products → New product** (your bot), then a **policy**, e.g. "Standard", *1 machine*: a machine is
   wherever the bot runs.
2. **API keys → Create API key**. It can only validate licenses, so it's safe inside a jar.
3. Optional, for the offline fallback: open any license and copy **Public key**.

## 2. Make it yours

1. Use this template (or copy the folder), then rename `com.example.mybot`, `MyBot` and `my-bot`
   (`settings.gradle.kts`, `build.gradle.kts`'s `mainClass`, the Java package).
2. Put the API key in your **user** Gradle properties (`~/.gradle/gradle.properties`), never in git:

   ```properties
   sunlicenseApiKey=slk_...
   sunlicensePublicKey=MCowBQYDK2VwAyEA...
   ```

   For a local SunLicense stack add `sunlicenseUrl=http://localhost`.
3. Create a bot at [discord.com/developers](https://discord.com/developers/applications), then
   `DISCORD_TOKEN=... LICENSE_KEY=... ./gradlew run` (it works in `run/`, where it writes
   `config.properties` on first start). Invite the bot to a test server and try `/license`.
4. Build: `./gradlew build` → `build/libs/my-bot-1.0.0.jar` (Java 21 needed; Gradle downloads itself).
   Buyers run it with `java -jar my-bot-1.0.0.jar` and fill in the `config.properties` it writes.

Your features go in `core/CoreFeatures.java`, started by `MyBot.startFeatures()`, which logs in to Discord only once the license is valid.

## 3. Sell it

| Where | How the key gets to the bot |
|---|---|
| [BuiltByBit](https://docs.sunlicense.com/integrations/builtbybit), [Voxel Shop](https://docs.sunlicense.com/integrations/voxel-shop) | Create a `%%__SUNLICENSE__%%` placeholder in the store. It replaces `BUILT_IN_KEY` in `MyBot` with each buyer's key on download, so there's nothing to configure |
| [Tebex](https://docs.sunlicense.com/integrations/tebex), [Gumroad](https://docs.sunlicense.com/integrations/gumroad), [Stripe](https://docs.sunlicense.com/integrations/stripe), [your Discord server](https://docs.sunlicense.com/guide/discord) and the other stores | The buyer gets a key by email, on the redeem page or from the SunLicense Discord bot, and puts it in `config.properties` |

## What happens when the bot starts

| SunLicense answers | The bot |
|---|---|
| Valid | Logs in to Discord; logs the masked key |
| Expired, revoked, suspended, unknown key, too many machines, blacklisted | Logs why (see `license/Messages.java`), logs out and exits with code 1 |
| Nothing (outage, network, rate limit) | Uses `license.lic` next to the bot if it's a valid offline license for this key; otherwise keeps running and retries every 10 minutes (`ALLOW_WHEN_UNREACHABLE = false` to wait instead) |

Each machine keeps a random id in `.machine-id` next to the bot, which is what counts against the
policy's machine limit.

Developing against your own license? Add an **exclusion** for your machine or IP in SunLicense
([blacklists and exclusions](https://docs.sunlicense.com/guide/access-rules)) so test runs don't use up slots.

**Usage statistics.** Every 30 minutes, in the background, the template tells SunLicense its version,
the Java and JDA version, OS, architecture and CPU cores, so you see who runs what on the product's
**Analytics** tab (with the license and machine, like bStats). No user names, IP addresses or server
names. Buyers switch it off with `analytics=false` in `config.properties` or `SUNLICENSE_ANALYTICS=off`;
it never affects the license check. What is sent:
[docs](https://docs.sunlicense.com/guide/sdks#built-in-analytics).

## Protection

Release builds go further than a license check that an AI editor or a cracker could delete:

- **Sealed core.** Everything in the `core` package (`core/CoreFeatures.java`: your features) is encrypted in the
  release jar by `sunlicense seal`. Only a valid license check returns the key that opens it, so a copy whose check
  was cut out has nothing to run. The key from the last valid check is kept for 7 days, encrypted for this
  machine (a `.sunlicense-unlock-…` file next to `license.lic`), and an offline license file carries it too, so
  an outage doesn't stop your customers. A suspended, expired or revoked license gets no key.
- **Tamper reports.** The seal carries the SHA-256 of your other classes. A modified jar still starts, but says so
  in its next license check, and [Sentinel](https://docs.sunlicense.com/guide/sentinel) suggests suspending that license.
- **Ownership notice.** The header comment in `MyBot.java` and one start-up line
  (`MyBot is licensed software by You. License AX8P-XXXX-XXXX-XXXX. Terms: …`): set `AUTHOR` and `TERMS`.

Build a release with the [SunLicense CLI](https://docs.sunlicense.com/cli/) signed in (or `SUNLICENSE_TOKEN` set):

```bash
./gradlew build -PsunlicenseProduct=<your product code>
```

Ship `build/release/<jar>`. Without the property, `./gradlew build` is a development build with the core in
plain: test with it, don't sell it. In GitHub Actions, add the secret `SUNLICENSE_TOKEN` (a personal access
token) and the variable `SUNLICENSE_PRODUCT`, and the workflow builds the release jar. Code the platform loads by
name (the main class, `MyBot`) stays outside `core`.

Honest limit: someone with a valid license can still pull the key out of memory and patch their copy; that copy
then reports itself as modified. [How protection works](https://docs.sunlicense.com/guide/protection).

## Files

```
build.gradle.kts                          JDA, SDK, Shadow, settings baked into the jar
src/main/java/.../MyBot.java              start-up, re-checks, Discord login, /license, your features
src/main/java/.../core/CoreFeatures.java    your features: the core, sealed in release builds
src/main/java/.../license/SealedCore.java  loads the core (sealed or plain) and checks the jar
src/main/java/.../license/SunLicenseProtection.java unlock, cache, integrity, notice (a copy of the SDK's; replace, don't edit)
src/main/java/.../license/LicenseGuard.java   online check → offline file → status (no Discord code, tested)
src/main/java/.../license/OfflineLicense.java Ed25519 offline license file check
src/main/java/.../license/Messages.java   what the bot's owner reads
src/main/java/.../license/SunLicenseAnalytics.java anonymous usage statistics (a copy of the SDK's; replace, don't edit)
src/main/resources/config.properties      discord-token, license-key, recheck-hours, analytics
src/test/java/.../LicenseGuardTest.java   every result against a stub SunLicense
src/test/java/.../SealedCoreTest.java     the sealed core opens only with a valid unlock
.github/workflows/build.yml               CI
```

## Licence

[MIT No Attribution](LICENSE): copy any of it into your bot, commercial or not, without credit.
