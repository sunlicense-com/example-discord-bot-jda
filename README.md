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

Your features go in `MyBot.startFeatures()`, which logs in to Discord only once the license is valid.

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

## Files

```
build.gradle.kts                          JDA, SDK, Shadow, settings baked into the jar
src/main/java/.../MyBot.java              start-up, re-checks, Discord login, /license, your features
src/main/java/.../license/LicenseGuard.java   online check → offline file → status (no Discord code, tested)
src/main/java/.../license/OfflineLicense.java Ed25519 offline license file check
src/main/java/.../license/Messages.java   what the bot's owner reads
src/main/resources/config.properties      discord-token, license-key, recheck-hours
src/test/java/.../LicenseGuardTest.java   every result against a stub SunLicense
.github/workflows/build.yml               CI
```

## Licence

[MIT No Attribution](LICENSE): copy any of it into your bot, commercial or not, without credit.
