package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.command.SubCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.kit.KitApplier;
import me.alpha432.corepvp.kit.KitManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** {@code /corepvp kit list|give|save} */
public final class KitSubCommand extends SubCommand {

    private final KitManager kits;
    private final KitApplier applier;
    private final Messages messages;

    public KitSubCommand(KitManager kits, KitApplier applier, Messages messages) {
        super("kit", "corepvp.command.admin", "kit <list|give|save>",
                "Inspect and edit kits.", false);
        this.kits = kits;
        this.applier = applier;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "general.usage",
                    Messages.of("label", label), Messages.of("usage", usage()));
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "give" -> give(sender, args);
            case "save" -> save(sender, args);
            default -> messages.send(sender, "general.usage",
                    Messages.of("label", label), Messages.of("usage", usage()));
        }
    }

    private void list(CommandSender sender) {
        List<Kit> all = kits.all();
        if (all.isEmpty()) {
            messages.send(sender, "kit.none");
            return;
        }
        sender.sendMessage(messages.render("kit.list-header", Messages.of("count", all.size())));
        for (Kit kit : all) {
            sender.sendMessage(messages.render("kit.list-entry",
                    Messages.of("id", kit.id()),
                    Messages.of("name", kit.displayName()),
                    Messages.of("combat", kit.combatMode().name().toLowerCase(Locale.ROOT)),
                    Messages.of("arenas", String.join(", ", kit.arenaTypes()))));
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "kit.give-usage");
            return;
        }
        Kit kit = kits.byId(args[1]);
        if (kit == null) {
            messages.send(sender, "kit.not-found", Messages.of("input", args[1]));
            return;
        }
        applier.apply(player, kit);
        messages.send(sender, "kit.given", Messages.of("name", kit.displayName()));
    }

    /** Overwrites a kit's loadout with whatever the admin is currently holding. */
    private void save(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "kit.save-usage");
            return;
        }
        Kit kit = kits.byId(args[1]);
        if (kit == null) {
            messages.send(sender, "kit.not-found", Messages.of("input", args[1]));
            return;
        }
        kit.contents(player.getInventory().getContents());
        kit.armor(player.getInventory().getArmorContents());
        kit.offHand(player.getInventory().getItemInOffHand());
        kits.save(kit);
        messages.send(sender, "kit.saved", Messages.of("name", kit.displayName()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return List.of("list", "give", "save");
        }
        if (args.length == 2) {
            return kits.ids();
        }
        return List.of();
    }
}
