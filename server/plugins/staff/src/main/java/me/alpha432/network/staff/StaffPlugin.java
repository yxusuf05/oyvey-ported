package me.alpha432.network.staff;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.core.text.Messages;
import me.alpha432.network.core.text.Msg;
import me.alpha432.network.staff.command.HistoryCommands;
import me.alpha432.network.staff.command.LiftCommands;
import me.alpha432.network.staff.command.PunishCommands;
import me.alpha432.network.staff.command.StaffChatCommand;
import me.alpha432.network.staff.listener.StaffListener;
import me.alpha432.network.staff.punishment.Punishment;
import me.alpha432.network.staff.punishment.PunishmentService;
import me.alpha432.network.staff.punishment.PunishmentType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Moderation: punishments, the ban screen, the staff chat and who may punish whom. */
public final class StaffPlugin extends JavaPlugin {

    private Messages messages;
    private PunishmentService punishments;
    private final Set<UUID> staffChatting = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        Core.database().applySchema(this, "schema.sql");
        punishments = new PunishmentService(this, Core.database());

        Bukkit.getPluginManager().registerEvents(new StaffListener(this), this);

        PunishCommands.register(this);
        LiftCommands.register(this);
        HistoryCommands.register(this);
        new StaffChatCommand(this).register();

        long interval = getConfig().getLong("expiry-check-minutes", 5L) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> punishments.expireOutdated(), interval, interval);

        getLogger().info("NetworkStaff enabled.");
    }

    /**
     * Looks the target up by name — online first, then in the profile table — and runs the
     * action on the main thread.
     */
    public void resolve(CommandSender sender, String name, Consumer<PlayerProfile> action) {
        Core.profiles().lookup(name).thenAccept(profile -> Bukkit.getScheduler().runTask(this, () -> {
            if (profile == null) {
                messages.send(sender, "punish.unknown-player", "<name>", name);
                return;
            }
            action.accept(profile);
        }));
    }

    /**
     * Staff cannot punish someone of the same or a higher rank. The console always may, and so
     * does anyone holding {@code network.staff.bypass} against a lower rank.
     */
    public boolean mayPunish(CommandSender sender, PlayerProfile target) {
        if (!(sender instanceof Player actor)) {
            return true;
        }
        if (actor.getUniqueId().equals(target.uuid())) {
            return false;
        }
        int actorWeight = Core.ranks().of(actor).weight();
        int targetWeight = Core.ranks().of(target).weight();
        return actorWeight > targetWeight;
    }

    /** Carries out the effect of a punishment that was just handed out. */
    public void apply(Punishment punishment) {
        Player online = Bukkit.getPlayer(punishment.target());
        switch (punishment.type()) {
            case KICK -> {
                if (online != null) {
                    online.kick(kickScreen(punishment));
                }
            }
            case BAN -> {
                if (online != null) {
                    online.kick(banScreen(punishment));
                }
            }
            case MUTE -> {
                if (online != null) {
                    sendMuteNotice(online, punishment);
                }
            }
            case WARN -> {
                if (online != null) {
                    messages.send(online, "punish.warn-notify",
                            "<reason>", punishment.reason(), "<actor>", punishment.actor());
                }
            }
        }
    }

    /** Tells the staff and, when configured, the whole server about a punishment. */
    public void announce(CommandSender actor, Punishment punishment) {
        String duration = punishment.isPermanent()
                ? messages.raw("history.state-permanent")
                : Durations.format(punishment.remainingMillis());

        messages.send(actor, "punish.done",
                "<player>", punishment.targetName(),
                "<type>", typeName(punishment.type()),
                "<duration>", duration,
                "<reason>", punishment.reason());

        notifyStaff("punish.broadcast-staff",
                "<actor>", actor.getName(),
                "<player>", punishment.targetName(),
                "<type>", typeName(punishment.type()),
                "<duration>", duration,
                "<reason>", punishment.reason());

        if (getConfig().getBoolean("broadcast-punishments", true)
                && punishment.type() != PunishmentType.WARN) {
            Bukkit.broadcast(messages.get("punish.broadcast-public",
                    "<player>", punishment.targetName(),
                    "<type>", typeName(punishment.type()),
                    "<duration>", duration,
                    "<reason>", punishment.reason()));
        }
    }

    public void notifyStaff(String key, Object... placeholders) {
        Component message = messages.get(key, placeholders);
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("network.staff")) {
                player.sendMessage(message);
            }
        }
    }

    public void sendStaffChat(String author, String text) {
        Component message = messages.get("staffchat.format",
                "<player>", author, "<message>", Msg.escape(text));
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("network.staff.staffchat")) {
                player.sendMessage(message);
            }
        }
    }

    public boolean toggleStaffChat(Player player) {
        if (!staffChatting.add(player.getUniqueId())) {
            staffChatting.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean isStaffChatting(Player player) {
        return staffChatting.contains(player.getUniqueId())
                && player.hasPermission("network.staff.staffchat");
    }

    public void clearStaffChat(Player player) {
        staffChatting.remove(player.getUniqueId());
    }

    public void sendMuteNotice(Player player, Punishment mute) {
        messages.send(player, mute.isPermanent() ? "punish.muted-permanent" : "punish.muted-temporary",
                "<reason>", mute.reason(),
                "<time>", Durations.format(mute.remainingMillis()));
    }

    public Component banScreen(Punishment ban) {
        return screen(ban, ban.isPermanent() ? "screen.ban-permanent" : "screen.ban-temporary");
    }

    public Component kickScreen(Punishment kick) {
        return screen(kick, "screen.kick");
    }

    private Component screen(Punishment punishment, String key) {
        List<String> lines = messages.rawList(key);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(lines.get(i));
        }
        return Msg.mm(text.toString(),
                "<reason>", punishment.reason(),
                "<actor>", punishment.actor(),
                "<time>", punishment.isPermanent() ? "" : Durations.format(punishment.remainingMillis()));
    }

    /** The word shown to players for a punishment type, taken from messages.yml. */
    public String typeName(PunishmentType type) {
        return messages.raw("type." + type.name().toLowerCase());
    }

    public Messages messages() {
        return messages;
    }

    public PunishmentService punishments() {
        return punishments;
    }
}
