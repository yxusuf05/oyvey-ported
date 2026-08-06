package me.alpha432.network.smp.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.core.util.Sounds;
import me.alpha432.network.smp.SmpPlugin;
import me.alpha432.network.smp.shop.ShopMenu;
import me.alpha432.network.smp.shop.ShopService;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** {@code /shop} and {@code /sell hand|all}. */
public final class ShopCommands {

    private ShopCommands() {
    }

    public static void register(SmpPlugin plugin) {
        new ShopCommand(plugin).register();
        new SellCommand(plugin).register();
    }

    private static final class ShopCommand extends BaseCommand {

        private final SmpPlugin smp;

        private ShopCommand(SmpPlugin plugin) {
            super(plugin, "shop");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            new ShopMenu(smp, player).open(player);
        }
    }

    private static final class SellCommand extends BaseCommand {

        private final SmpPlugin smp;

        private SellCommand(SmpPlugin plugin) {
            super(plugin, "sell");
            this.smp = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            String mode = args.length > 0 ? args[0].toLowerCase() : "hand";
            switch (mode) {
                case "hand" -> sellHand(player);
                case "all" -> sellAll(player);
                default -> smp.messages().send(sender, "sell.usage");
            }
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? List.of("hand", "all") : List.of();
        }

        private void sellHand(Player player) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                smp.messages().send(player, "sell.empty-hand");
                return;
            }
            ShopService.Entry entry = smp.shop().entry(hand.getType());
            if (entry == null || !entry.canSell()) {
                smp.messages().send(player, "sell.not-sellable", "<item>", hand.getType().name());
                return;
            }
            int amount = hand.getAmount();
            double payout = smp.shop().sellPrice(player, entry) / entry.amount() * amount;
            player.getInventory().setItemInMainHand(null);
            pay(player, payout, amount, hand.getType());
        }

        private void sellAll(Player player) {
            double payout = 0;
            int total = 0;
            ItemStack[] contents = player.getInventory().getStorageContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null) {
                    continue;
                }
                ShopService.Entry entry = smp.shop().entry(stack.getType());
                if (entry == null || !entry.canSell()) {
                    continue;
                }
                payout += smp.shop().sellPrice(player, entry) / entry.amount() * stack.getAmount();
                total += stack.getAmount();
                contents[slot] = null;
            }
            if (total == 0) {
                smp.messages().send(player, "sell.nothing");
                return;
            }
            player.getInventory().setStorageContents(contents);
            pay(player, payout, total, null);
        }

        private void pay(Player player, double payout, int amount, Material material) {
            PlayerProfile profile = Core.profiles().get(player);
            Core.economy().deposit(profile, payout);
            Core.profiles().save(profile);
            smp.messages().send(player, material == null ? "sell.sold-all" : "sell.sold",
                    "<amount>", String.valueOf(amount),
                    "<item>", material == null ? "" : material.name(),
                    "<price>", Core.economy().format(payout));
            Sounds.play(player, smp.getConfig().getString("shop.sell-sound", ""), 1f, 1f);
        }
    }
}
