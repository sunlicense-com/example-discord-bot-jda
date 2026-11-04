package com.example.mybot.core;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import com.example.mybot.Features;
import com.example.mybot.MyBot;

/**
 * Your bot's features: its slash commands and listeners. Everything in this package is the "core": release builds
 * encrypt it, and only a valid license unlocks it (see {@code license/SealedCore}). Put the code your bot can't work
 * without here.
 */
public final class CoreFeatures implements Features {
    @Override
    public List<CommandData> commands() {
        return List.of(Commands.slash("hello", "Says hello"));
    }

    @Override
    public List<Object> listeners(MyBot bot) {
        return List.of(new ListenerAdapter() {
            @Override
            public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
                if (event.getName().equals("hello")) {
                    event.reply("Hello, " + event.getUser().getEffectiveName() + "!").queue();
                }
            }
        });
    }
}
