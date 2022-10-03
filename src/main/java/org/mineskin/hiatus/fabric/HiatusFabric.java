package org.mineskin.hiatus.fabric;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mineskin.hiatus.Account;
import org.mineskin.hiatus.MineSkinHiatus;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HiatusFabric implements ModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("MineSkinHiatus");

    File configFile;
    static MineSkinHiatus hiatus;

    public static boolean launchTracked = false;

    public static MineSkinHiatus getHiatus() {
        return hiatus;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Hello World!");

        FabricLoader fabricLoader = FabricLoader.getInstance();
        String version = fabricLoader
                .getModContainer("mineskin-hiatus")
                .map(ModContainer::getMetadata)
                .map(ModMetadata::getVersion)
                .map(Version::getFriendlyString)
                .orElse("unknown");
        LOGGER.info("Version: " + version);

        configFile = new File(fabricLoader.getConfigDir().toFile(), "mineskin-hiatus.json");
        hiatus = MineSkinHiatus.newInstance("fabric/" + version, configFile);

        GameProfile gameProfile = MinecraftClient.getInstance().getSession().getProfile();
        if (gameProfile != null) {
            LOGGER.info("Got game profile, attempting to load account info " + gameProfile.getName() + "/" + gameProfile.getId());
            UUID accountUuid = gameProfile.getId();
            if (accountUuid != null) {
                hiatus.trySetAccountFromUuid(accountUuid);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            hiatus.onGameClosing();
        }));

        Command<ServerCommandSource> help = context -> {
            context.getSource().sendMessage(Text.literal("\n")
                    .append(Text.literal("/mineskin hiatus list\n")
                            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/mineskin hiatus list"))))
                    .append(Text.literal("/mineskin hiatus add <token> <email>\n")
                            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/mineskin hiatus add "))))
                    .append(Text.literal("/mineskin hiatus remove <email>\n")
                            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/mineskin hiatus remove "))))
                    .append(Text.of("\n"))
                    .append(Text.literal("[Login on mineskin.org]")
                            .styled(style -> style.withBold(true)
                                    .withColor(Formatting.YELLOW)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://mineskin.org/account?hiatus=true"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to go to mineskin.org")))
                            ))
                    .append(Text.of(" to link your account"))
            );
            return Command.SINGLE_SUCCESS;
        };

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("mineskin")
                    .executes(help));
            dispatcher.register(literal("mineskin")
                    .then(literal("hiatus")
                            .executes(help)));

            dispatcher.register(literal("mineskin")
                    .then(literal("hiatus")
                            .then(literal("list")
                                    .executes(context -> {
                                        hiatus.getAccounts().thenAccept(accounts -> {
                                            if (accounts.isEmpty()) {
                                                context.getSource().sendMessage(Text.literal("No accounts registered"));
                                            } else {
                                                MutableText text = Text.literal("\nRegistered accounts:\n");
                                                for (Map.Entry<UUID, String> account : accounts.entrySet()) {
                                                    text.append(Text.literal(account.getValue() + " " + account.getKey())
                                                            .append(Text.literal(" [Remove]\n")
                                                                    .styled(style -> style.withBold(true)
                                                                            .withColor(Formatting.RED)
                                                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mineskin hiatus remove " + account.getValue()))
                                                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, MutableText.of(new LiteralTextContent("Click to remove"))))
                                                                    )
                                                            ));
                                                }
                                                context.getSource().sendMessage(text);
                                            }
                                        });
                                        return Command.SINGLE_SUCCESS;
                                    }))
                    ));
            dispatcher.register(literal("mineskin")
                    .then(literal("hiatus")
                            .then(literal("add")
                                    .then(argument("token", StringArgumentType.word())
                                            .then(argument("email", StringArgumentType.greedyString())
                                                    .executes(context -> {
                                                        String token = context.getArgument("token", String.class);
                                                        String email = context.getArgument("email", String.class);

                                                        GameProfile profile = MinecraftClient.getInstance().getSession().getProfile();
                                                        if (profile != null) {
                                                            if (!MineSkinHiatus.validateTokenAccount(profile.getId(), token)) {
                                                                context.getSource().sendError(Text.literal("Please launch the game with the account you're trying to link (invalid token)"));
                                                                return Command.SINGLE_SUCCESS;
                                                            }

                                                            hiatus.setAccount(new Account(profile.getId(), email, token));
                                                            context.getSource().sendMessage(Text.literal("\nAccount added!"));
                                                            hiatus.onGameLaunching(); // send as if the game just launched
                                                        } else {
                                                            context.getSource().sendError(Text.literal("Failed to add account, no game profile found!"));
                                                        }

                                                        return Command.SINGLE_SUCCESS;
                                                    }))))));
            dispatcher.register(literal("mineskin")
                    .then(literal("hiatus")
                            .then(literal("remove")
                                    .then(argument("email", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String email = context.getArgument("email", String.class);

                                                hiatus.getAccounts().thenAccept(accounts -> {
                                                    if (accounts.isEmpty()) {
                                                        context.getSource().sendError(Text.literal("No accounts registered"));
                                                    } else {
                                                        Map<String, UUID> invertedAccounts = accounts.entrySet()
                                                                .stream()
                                                                .collect(Collectors.toMap(e -> e.getValue().toLowerCase(), Map.Entry::getKey));
                                                        UUID uuid = invertedAccounts.get(email.toLowerCase());
                                                        if (uuid == null) {
                                                            context.getSource().sendError(Text.literal("Account not found"));
                                                            return;
                                                        }
                                                        hiatus.deleteAccount(uuid);
                                                        context.getSource().sendMessage(Text.literal("\nAccount removed!"));
                                                    }
                                                });

                                                return Command.SINGLE_SUCCESS;
                                            })))));
        });

    }


}
