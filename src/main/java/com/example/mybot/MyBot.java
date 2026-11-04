/*
 * MyBot by You. Licensed software: each machine needs its own license. Terms: https://example.com/terms
 * Licensing by SunLicense (https://sunlicense.com).
 */
package com.example.mybot;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;

import com.example.mybot.license.LicenseGuard;
import com.example.mybot.license.LicenseStatus;
import com.example.mybot.license.Messages;
import com.example.mybot.license.SealedCore;
import com.example.mybot.license.SunLicenseAnalytics;
import com.example.mybot.license.SunLicenseProtection;

/**
 * A Discord bot (JDA) protected by SunLicense. The license is checked at start-up and every
 * {@code recheck-hours}; the bot logs in to Discord only once it is valid ({@link #startFeatures()}) and
 * logs out and exits if SunLicense later says no ({@link #stopFeatures()}). Its features ({@code core/CoreFeatures})
 * are sealed in release builds, so only a valid license can run them.
 */
public final class MyBot {
    /**
     * BuiltByBit and Voxel Shop replace this string with the buyer's license key when they download the
     * jar (create a "%%__SUNLICENSE__%%" placeholder in the store, see docs.sunlicense.com/integrations).
     * Builds from anywhere else keep it, and the key comes from config.properties or LICENSE_KEY instead.
     */
    private static final String BUILT_IN_KEY = "%%__SUNLICENSE__%%";

    /**
     * When SunLicense can't be reached and there is no valid offline license file: keep running (true,
     * the default: an outage on our side shouldn't take your customers' bots down) or wait (false).
     */
    static final boolean ALLOW_WHEN_UNREACHABLE = true;

    /** How soon to try again after no answer, in minutes. */
    private static final long RETRY_MINUTES = 10;

    private static final Logger LOG = Logger.getLogger("MyBot");

    /** The core's entry class (sealed in release builds). */
    private static final String CORE = "com.example.mybot.core.CoreFeatures";

    /** The ownership notice logged at start-up: who makes the bot and where its terms are. */
    private static final String AUTHOR = "You";
    private static final String TERMS = "https://example.com/terms";

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Path home = Path.of("").toAbsolutePath();
    private final Properties config;
    private final LicenseGuard guard;
    private final SunLicenseAnalytics analytics;
    private final String licenseKey;
    private final String machineId;
    private ScheduledFuture<?> retry;
    private volatile LicenseStatus status;
    private JDA jda;

    public static void main(String[] args) throws IOException {
        new MyBot().start();
    }

    private MyBot() throws IOException {
        config = loadConfig();
        LicenseGuard.Settings settings = LicenseGuard.Settings.load(MyBot.class.getResourceAsStream("/sunlicense.properties"));
        guard = new LicenseGuard(settings, home.resolve("license.lic"),
                SunLicenseProtection.builder().product("MyBot", AUTHOR).terms(TERMS));
        licenseKey = LicenseGuard.pickKey(BUILT_IN_KEY, setting("LICENSE_KEY", "license-key"));
        machineId = machineId();
        guard.protection().license(licenseKey, machineId);
        LOG.info(guard.protection().notice());
        SealedCore.checkEarly(guard.protection(), MyBot.class.getClassLoader());   // so the first check reports a modified jar
        // Anonymous usage statistics for your Analytics tab, every 30 minutes on a background thread (config: analytics).
        analytics = SunLicenseAnalytics.builder().baseUrl(settings.url()).apiKey(settings.apiKey())
                .licenseKey(licenseKey).fingerprint(machineId).productVersion(MyBot.class.getPackage().getImplementationVersion())
                .enabled(!"false".equalsIgnoreCase(config.getProperty("analytics", "true").trim())).start();
    }

    private void start() {
        if (setting("DISCORD_TOKEN", "discord-token").isBlank()) {
            LOG.severe("Set discord-token in config.properties (or DISCORD_TOKEN).");
            System.exit(1);
        }
        scheduler.execute(this::check);
        long hours = Long.parseLong(config.getProperty("recheck-hours", "6").trim());
        if (hours > 0) {
            scheduler.scheduleAtFixedRate(this::check, hours, hours, TimeUnit.HOURS);
        }
    }

    /** Runs on the scheduler thread, never on JDA's threads. */
    private void check() {
        LicenseStatus result = guard.check(licenseKey, machineId);
        status = result;
        switch (result.state()) {
            case VALID, VALID_OFFLINE -> {
                if (jda == null) {
                    LOG.info("License " + Messages.mask(licenseKey) + ": " + result.message());
                    startFeatures();
                }
            }
            case REJECTED, NOT_CONFIGURED -> {
                LOG.severe("License " + Messages.mask(licenseKey) + ": " + result.message());
                LOG.severe("The bot is stopped until this is fixed.");
                stopFeatures();
                System.exit(1);
            }
            case UNREACHABLE -> {
                LOG.warning(result.message() + " Trying again in " + RETRY_MINUTES + " minutes.");
                if (ALLOW_WHEN_UNREACHABLE && jda == null) {
                    startFeatures();
                } else if (jda == null) {
                    LOG.severe("The bot needs a license check to start; it will start once SunLicense answers.");
                }
                if (retry == null || retry.isDone()) {   // one retry at a time
                    retry = scheduler.schedule(this::check, RETRY_MINUTES, TimeUnit.MINUTES);
                }
            }
        }
    }

    /**
     * Logs in with the core's commands and listeners (core/CoreFeatures). In a release build the core needs the unlock
     * from a valid license check; without it the bot stays logged out and tries again with the next check.
     */
    private void startFeatures() {
        Features features;
        try {
            features = SealedCore.load(guard.protection(), MyBot.class.getClassLoader(), CORE, Features.class);
        } catch (SunLicenseProtection.Locked e) {
            LOG.severe("The bot can't start yet: " + e.getMessage()
                    + " It starts as soon as SunLicense confirms the license (the confirmation is kept for 7 days).");
            return;
        } catch (Exception e) {
            LOG.severe("The bot's core failed to load: " + e);
            System.exit(1);
            return;
        }
        try {
            jda = JDABuilder.createLight(setting("DISCORD_TOKEN", "discord-token"))
                    .addEventListeners(new Listener(features.commands()))
                    .addEventListeners(features.listeners(this).toArray())
                    .build();
        } catch (InvalidTokenException e) {
            LOG.severe("Discord refused the bot token: " + e.getMessage());
            System.exit(1);
        }
    }

    private void stopFeatures() {
        analytics.close();
        if (jda != null) {
            jda.shutdown();
            jda = null;
        }
    }

    private final class Listener extends ListenerAdapter {
        private final List<CommandData> commands = new ArrayList<>();

        Listener(List<CommandData> coreCommands) {
            commands.add(Commands.slash("license", "Shows this bot's license status"));
            commands.addAll(coreCommands);
        }

        @Override
        public void onReady(ReadyEvent event) {
            event.getJDA().updateCommands().addCommands(commands).queue();
            LOG.info("Logged in as " + event.getJDA().getSelfUser().getName() + ".");
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            if (!event.getName().equals("license")) {
                return;
            }
            LicenseStatus s = status;
            String text = s == null ? "The license hasn't been checked yet."
                    : "License " + Messages.mask(licenseKey) + " (" + s.state() + "): " + s.message();
            event.reply(text).setEphemeral(true).queue();
        }
    }

    /** An environment variable, else the config.properties entry. */
    private String setting(String env, String key) {
        String v = System.getenv(env);
        return v != null && !v.isBlank() ? v.trim() : config.getProperty(key, "").trim();
    }

    /** config.properties in the working directory, written from the default on first start. */
    private Properties loadConfig() throws IOException {
        Path file = home.resolve("config.properties");
        if (!Files.exists(file)) {
            try (InputStream in = MyBot.class.getResourceAsStream("/config.properties")) {
                Files.copy(in, file);
            }
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }

    /**
     * A random id for this machine, kept next to the bot, so restarts count as the same machine. Copying
     * the folder to another machine copies the id too; the license's machine limit still applies to each id.
     */
    private String machineId() throws IOException {
        Path file = home.resolve(".machine-id");
        if (Files.isRegularFile(file)) {
            String id = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (!id.isEmpty()) {
                return id;
            }
        }
        String id = "discord-bot-" + UUID.randomUUID();
        Files.writeString(file, id, StandardCharsets.UTF_8);
        return id;
    }
}
