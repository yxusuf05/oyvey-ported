package me.alpha432.network.lobby;

import me.alpha432.network.core.text.Messages;
import me.alpha432.network.lobby.command.LobbyCommand;
import me.alpha432.network.lobby.command.SelectorCommand;
import me.alpha432.network.lobby.command.SetLobbyCommand;
import me.alpha432.network.lobby.listener.DoubleJumpListener;
import me.alpha432.network.lobby.listener.LobbyListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** The hub plugin: spawn handling, server selector, protection and double jump. */
public final class LobbyPlugin extends JavaPlugin {

    private Messages messages;
    private LobbyService lobby;
    private JoinItems joinItems;
    private VisibilityService visibility;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        lobby = new LobbyService(this);
        joinItems = new JoinItems(this);
        visibility = new VisibilityService(this);

        LobbyListener lobbyListener = new LobbyListener(this);
        Bukkit.getPluginManager().registerEvents(lobbyListener, this);
        Bukkit.getPluginManager().registerEvents(new DoubleJumpListener(this), this);
        Bukkit.getPluginManager().registerEvents(visibility, this);

        new LobbyCommand(this).register();
        new SetLobbyCommand(this).register();
        new SelectorCommand(this).register();

        // Players already online during a reload get the lobby state back.
        Bukkit.getOnlinePlayers().forEach(lobbyListener::prepare);

        if (Bukkit.getWorld(lobby.worldName()) == null) {
            getLogger().warning("Lobby world '" + lobby.worldName() + "' does not exist. "
                    + "Add it to NetworkCore's worlds.yml or change 'world' in this plugin's config.yml.");
        }
        getLogger().info("NetworkLobby enabled.");
    }

    public Messages messages() {
        return messages;
    }

    public LobbyService lobby() {
        return lobby;
    }

    public JoinItems joinItems() {
        return joinItems;
    }

    public VisibilityService visibility() {
        return visibility;
    }
}
