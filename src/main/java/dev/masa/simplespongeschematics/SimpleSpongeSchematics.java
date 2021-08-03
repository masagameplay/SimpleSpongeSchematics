package dev.masa.simplespongeschematics;

import com.google.inject.Inject;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.data.persistence.DataFormats;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.cause.entity.SpawnTypes;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.lifecycle.ConstructPluginEvent;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartingEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.world.schematic.Schematic;
import org.spongepowered.api.world.volume.archetype.ArchetypeVolume;
import org.spongepowered.math.vector.Vector3i;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.jvm.Plugin;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * An example Sponge plugin.
 *
 * <p>All methods are optional -- some common event registrations are included as a jumping-off point.</p>
 */
@Plugin("example")
public class SimpleSpongeSchematics {

    private final PluginContainer container;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, SchematicPlayer> schematicPlayers = new ConcurrentHashMap<>();

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path config;
    private Path schematicsDir;


    @Inject
    SimpleSpongeSchematics(final PluginContainer container, final Logger logger) {
        this.container = container;
        this.logger = logger;
    }

    @Listener
    public void onConstructPlugin(final ConstructPluginEvent event) throws IOException {
        this.schematicsDir = this.config.resolve("schematics");
        Files.createDirectories(this.config);
        // Perform any one-time setup
        this.logger.info("Constructing example plugin");
    }

    @Listener
    public void onServerStarting(final StartingEngineEvent<Server> event) {
        // Any setup per-game instance. This can run multiple times when
        // using the integrated (singleplayer) server.
    }

    @Listener
    public void onServerStopping(final StoppingEngineEvent<Server> event) {
        // Any tear down per-game instance. This can run multiple times when
        // using the integrated (singleplayer) server.
    }

    @Listener
    public void onInteractWithToolPrimary(InteractBlockEvent.Primary.Start event, @Root ServerPlayer player){
        if(!player.itemInHand(HandTypes.MAIN_HAND.get()).type().equals(ItemTypes.WOODEN_AXE.get())) return;
        if(!this.schematicPlayers.containsKey(player.uniqueId())) {
            this.schematicPlayers.put(player.uniqueId(), new SchematicPlayer(player.uniqueId()));
        }
        this.schematicPlayers.get(player.uniqueId()).firstPos(event.block().position());
        player.sendMessage(Component.text("First position saved.", NamedTextColor.GREEN));
        event.setCancelled(true);
    }

    @Listener
    public void onInteractWithToolSecondary(InteractBlockEvent.Secondary event, @Root ServerPlayer player){
        if(!player.itemInHand(HandTypes.MAIN_HAND.get()).type().equals(ItemTypes.WOODEN_AXE.get())) return;
        if(!this.schematicPlayers.containsKey(player.uniqueId())) {
            this.schematicPlayers.put(player.uniqueId(), new SchematicPlayer(player.uniqueId()));
        }
        this.schematicPlayers.get(player.uniqueId()).secondPos(event.block().position());
        player.sendMessage(Component.text("Second position saved.", NamedTextColor.GREEN));
        event.setCancelled(true);
    }

    @Listener
    public void onRegisterCommands(final RegisterCommandEvent<Command.Parameterized> event) {
        // Register a simple command
        // When possible, all commands should be registered within a command register event
        final Parameter.Value<String> fileName = Parameter.string().key("fileName").build();
        event.register(this.container, Command.builder()
                .permission("simplespongeschematics.copy")
                .addParameter(fileName)
                .executor(ctx -> {
                    Optional<Player> player = ctx.cause().first(Player.class);
                    if (player.isEmpty()) {
                        return CommandResult.error(Component.text("Only players can execute this command!", NamedTextColor.RED));
                    }

                    if (!this.schematicPlayers.containsKey(player.get().uniqueId())) {
                        return CommandResult.error(Component.text("You must set both positions before copying.", NamedTextColor.RED));
                    }

                    final String file = ctx.requireOne(fileName);
                    final Path desiredFilePath = this.schematicsDir.resolve(file + ".schem");
                    if (Files.exists(desiredFilePath)) {
                        return CommandResult.error(Component.text(file + " already exists, please delete the file first.", NamedTextColor.RED));
                    }
                    if (Files.isDirectory(desiredFilePath)) {
                        return CommandResult.error((Component.text(file + "is a directory, please use a file name.", NamedTextColor.RED)));
                    }

                    SchematicPlayer schematicPlayer = this.schematicPlayers.get(player.get().uniqueId());
                    ArchetypeVolume volume = schematicPlayer.clipboard();

                    if (volume == null) {
                        return CommandResult.error(Component.text("You must copy something before saving!", NamedTextColor.RED));
                    }

                    Schematic schematic = Schematic.builder()
                            .volume(volume)
                            .metaValue(Schematic.METADATA_AUTHOR, player.get().name())
                            .metaValue(Schematic.METADATA_NAME, file)
                            .build();


                    DataContainer schematicData = Sponge.dataManager().translator(Schematic.class)
                            .orElseThrow(
                                    () -> new IllegalStateException("Sponge doesn't have a DataTranslator for Schematics!"))
                            .translate(schematic);

                    try {
                        final Path output = Files.createFile(desiredFilePath);
                        DataFormats.NBT.get().writeTo(new GZIPOutputStream(Files.newOutputStream(output)), schematicData);
                        player.get().sendMessage(Component.text("Saved schematic to " + output.toAbsolutePath() + ".", NamedTextColor.GREEN));
                    } catch (final Exception e) {
                        e.printStackTrace();
                        final StringWriter writer = new StringWriter();
                        e.printStackTrace(new PrintWriter(writer));
                        final Component errorText = Component.text(writer.toString().replace("\t", "    ")
                                .replace("\r\n", "\n")
                                .replace("\r", "\n")
                        );

                        return CommandResult.error(Component.text(
                                "Error saving schematic: " + e.getMessage(), NamedTextColor.RED)
                                .hoverEvent(HoverEvent.showText(errorText)));
                    }
                    return CommandResult.success();
                })
                .build(), "save", "/save");


        event.register(
                this.container,
                Command.builder()
                        .permission("simplespongeschematics.load")
                        .addParameter(fileName)
                        .executor(ctx -> {
                            Optional<Player> player = ctx.cause().first(Player.class);
                            if (player.isEmpty()) {
                                return CommandResult.error(Component.text("Only players can execute this command!", NamedTextColor.RED));
                            }

                            final String file = ctx.requireOne(fileName);
                            final Path desiredFilePath = this.schematicsDir.resolve(file + ".schem");
                            if (!Files.isRegularFile(desiredFilePath)) {
                                throw new CommandException(Component.text("File " + file + " was not a normal schemaic file"));
                            }
                            final Schematic schematic;
                            final DataContainer schematicContainer;
                            try (final GZIPInputStream stream = new GZIPInputStream(Files.newInputStream(desiredFilePath))) {
                                schematicContainer = DataFormats.NBT.get().readFrom(stream);
                            } catch (IOException e) {
                                e.printStackTrace();
                                final StringWriter writer = new StringWriter();
                                e.printStackTrace(new PrintWriter(writer));
                                final Component errorText = Component.text(writer.toString().replace("\t", "    ")
                                        .replace("\r\n", "\n")
                                        .replace("\r", "\n")
                                );

                                return CommandResult.error(Component.text(
                                        "Error loading schematic: " + e.getMessage(), NamedTextColor.RED)
                                        .hoverEvent(HoverEvent.showText(errorText)));
                            }
                            schematic = Sponge.dataManager().translator(Schematic.class)
                                    .orElseThrow(() -> new IllegalStateException("Expected a DataTranslator for a Schematic"))
                                    .translate(schematicContainer);
                            player.get().sendMessage(Component.text("Loaded schematic from " + file, NamedTextColor.GREEN));

                            if (!this.schematicPlayers.containsKey(player.get().uniqueId())) {
                                this.schematicPlayers.put(player.get().uniqueId(), new SchematicPlayer(player.get().uniqueId()));
                            }

                            final SchematicPlayer schematicPlayer = this.schematicPlayers.get(player.get().uniqueId());
                            schematicPlayer.clipboard(schematic);
                            schematicPlayer.origin(player.get().blockPosition());
                            return CommandResult.success();
                        })
                        .build(),
                "load", "/load"
        );

        event.register(this.container, Command.builder()
                .permission("simplespongeschematics.copy")
                .executor(ctx -> {
                    Optional<Player> player = ctx.cause().first(Player.class);
                    if (player.isEmpty()) {
                        return CommandResult.error(Component.text("Only players can execute this command!", NamedTextColor.RED));
                    }

                    if (!this.schematicPlayers.containsKey(player.get().uniqueId())) {
                        this.schematicPlayers.put(player.get().uniqueId(), new SchematicPlayer(player.get().uniqueId()));
                    }

                    SchematicPlayer schematicPlayer = this.schematicPlayers.get(player.get().uniqueId());

                    if (schematicPlayer.firstPos() == null || schematicPlayer.secondPos() == null) {
                        return CommandResult.error(Component.text("You must set both positions before copying.", NamedTextColor.RED));
                    }

                    Vector3i min = schematicPlayer.firstPos().min(schematicPlayer.secondPos());
                    Vector3i max = schematicPlayer.firstPos().max(schematicPlayer.secondPos());
                    schematicPlayer.origin(player.get().blockPosition());
                    ArchetypeVolume volume = player.get().world().createArchetypeVolume(min, max, schematicPlayer.origin());
                    schematicPlayer.clipboard(volume);
                    player.get().sendMessage(Component.text("Saved to clipboard.", NamedTextColor.GREEN));
                    return CommandResult.success();
                })
                .build(), "copy", "/copy");

        event.register(this.container, Command.builder()
                .permission("simplespongeschematics.paste")
                .executor(ctx -> {
                    Optional<ServerPlayer> player = ctx.cause().first(ServerPlayer.class);
                    if (player.isEmpty()) {
                        return CommandResult.error(Component.text("Only players can execute this command!", NamedTextColor.RED));
                    }

                    if (!this.schematicPlayers.containsKey(player.get().uniqueId())) {
                        this.schematicPlayers.put(player.get().uniqueId(), new SchematicPlayer(player.get().uniqueId()));
                    }

                    SchematicPlayer schematicPlayer = this.schematicPlayers.get(player.get().uniqueId());

                    ArchetypeVolume volume = schematicPlayer.clipboard();
                    if (volume == null) {
                        return CommandResult.error(Component.text("You must copy or load something before pasting!", NamedTextColor.RED));
                    }
                    try (final CauseStackManager.StackFrame frame = Sponge.server().causeStackManager().pushCauseFrame()) {
                        frame.pushCause(this.container);
                        volume.applyToWorld(player.get().world(), player.get().blockPosition(), SpawnTypes.PLACEMENT);
                    }

                    player.get().sendMessage(Component.text("Pasted clipboard.", NamedTextColor.GREEN));
                    return CommandResult.success();
                })
                .build(), "paste", "/paste");


        event.register(this.container, Command.builder()
                .permission("simplespongeschematics.setpos")
                .executor(ctx -> {
                    Optional<Player> player = ctx.cause().first(Player.class);
                    if (player.isEmpty()) {
                        return CommandResult.error(Component.text("Only players can execute this command!", NamedTextColor.RED));
                    }

                    if (!this.schematicPlayers.containsKey(player.get().uniqueId())) {
                        this.schematicPlayers.put(player.get().uniqueId(), new SchematicPlayer(player.get().uniqueId()));
                    }

                    this.schematicPlayers.get(player.get().uniqueId()).firstPos(player.get().blockPosition());

                    player.get().sendMessage(Component.text("First position saved.", NamedTextColor.GREEN));
                    return CommandResult.success();
                })
                .build(), "pos1", "/pos1");

        event.register(this.container, Command.builder()
                .permission("simplespongeschematics.setpos")
                .executor(ctx -> {
                    Optional<Player> player = ctx.cause().first(Player.class);
                    if (player.isEmpty()) {
                        return CommandResult.error(Component.text("Only players can execute this command!", NamedTextColor.RED));
                    }

                    if (!this.schematicPlayers.containsKey(player.get().uniqueId())) {
                        this.schematicPlayers.put(player.get().uniqueId(), new SchematicPlayer(player.get().uniqueId()));
                    }

                    this.schematicPlayers.get(player.get().uniqueId()).secondPos(player.get().blockPosition());

                    player.get().sendMessage(Component.text("Second position saved.", NamedTextColor.GREEN));
                    return CommandResult.success();
                })
                .build(), "pos2", "/pos2");
    }

}
