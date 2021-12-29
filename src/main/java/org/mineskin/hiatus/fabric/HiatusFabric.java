package org.mineskin.hiatus.fabric;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mineskin.hiatus.Account;
import org.mineskin.hiatus.MineSkinHiatus;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.literal;

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

        CommandDispatcher<FabricClientCommandSource> dispatcher = ClientCommandManager.DISPATCHER;
        dispatcher.register(literal("mineskin")
                .executes(context -> {
                    context.getSource().sendFeedback(Text.of("/mineskin hiatus"));
                    return Command.SINGLE_SUCCESS;
                }));
        dispatcher.register(literal("mineskin")
                .then(literal("hiatus")
                        .executes(context -> {
                            context.getSource().sendFeedback(Text.of("/mineskin hiatus add <token> <email>"));
                            return Command.SINGLE_SUCCESS;
                        })));

        dispatcher.register(literal("mineskin")
                .then(literal("hiatus")
                        .then(literal("list")
                                .executes(context -> {
                                    hiatus.getAccounts().thenAccept(accounts -> {
                                        if (accounts.isEmpty()) {
                                            context.getSource().sendFeedback(Text.of("No accounts registered"));
                                        } else {
                                            StringBuilder msg = new StringBuilder("Registered accounts: ");
                                            for (Map.Entry<UUID, String> account : accounts.entrySet()) {
                                                msg.append(account.getValue()).append(" ").append(account.getKey()).append("\n");
                                            }
                                            context.getSource().sendFeedback(Text.of(msg.toString()));
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

                                                    if (gameProfile != null) {
                                                        hiatus.setAccount(new Account(gameProfile.getId(), email, token));
                                                        context.getSource().sendFeedback(Text.of("Account added!"));
                                                        hiatus.onGameLaunching(); // send as if the game just launched
                                                    } else {
                                                        context.getSource().sendError(Text.of("Failed to add account, no game profile found!"));
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
                                                    context.getSource().sendError(Text.of("No accounts registered"));
                                                } else {
                                                    Map<String, UUID> invertedAccounts = accounts.entrySet()
                                                            .stream()
                                                            .collect(Collectors.toMap(e -> e.getValue().toLowerCase(), Map.Entry::getKey));
                                                    UUID uuid = invertedAccounts.get(email.toLowerCase());
                                                    if (uuid == null) {
                                                        context.getSource().sendError(Text.of("Account not found"));
                                                        return;
                                                    }
                                                    hiatus.deleteAccount(uuid);
                                                    context.getSource().sendFeedback(Text.of("Account removed!"));
                                                }
                                            });

                                            return Command.SINGLE_SUCCESS;
                                        })))));

    }


}
