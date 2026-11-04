package com.example.mybot;

import java.util.List;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * The bot's features, given to JDA when the bot logs in (only once the license is valid). The implementation lives in
 * the {@code core} package, which release builds ship sealed (see {@code license/SealedCore}): keep this interface
 * small and outside it.
 */
public interface Features {
    /** Slash commands the core answers; registered when the bot is ready. */
    default List<CommandData> commands() {
        return List.of();
    }

    /** JDA event listeners (e.g. {@code ListenerAdapter}s) for those commands and anything else the bot does. */
    List<Object> listeners(MyBot bot);
}
