package me.alpha432.network.core.economy;

import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.core.profile.ProfileService;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Money lives on the {@link PlayerProfile}; this service is the only thing that changes it. */
public final class EconomyService {

    private final ProfileService profiles;
    private final String symbol;
    private final DecimalFormat format;

    public EconomyService(ProfileService profiles, String symbol, String pattern) {
        this.profiles = profiles;
        this.symbol = symbol;
        this.format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
    }

    public double balance(Player player) {
        PlayerProfile profile = profiles.get(player);
        return profile == null ? 0.0D : profile.balance();
    }

    public boolean has(PlayerProfile profile, double amount) {
        return profile != null && profile.balance() >= amount;
    }

    public void deposit(PlayerProfile profile, double amount) {
        if (profile == null || amount <= 0) {
            return;
        }
        profile.balance(profile.balance() + amount);
    }

    /** @return false when the amount is not positive or the profile cannot afford it. */
    public boolean withdraw(PlayerProfile profile, double amount) {
        if (profile == null || amount <= 0 || profile.balance() < amount) {
            return false;
        }
        profile.balance(profile.balance() - amount);
        return true;
    }

    public void set(PlayerProfile profile, double amount) {
        if (profile != null) {
            profile.balance(Math.max(0.0D, amount));
        }
    }

    /**
     * Moves money between two profiles and persists both. Fails without any change when the
     * amount is not positive or the sender cannot afford it.
     */
    public boolean transfer(PlayerProfile from, PlayerProfile to, double amount) {
        if (from == null || to == null || from.uuid().equals(to.uuid())) {
            return false;
        }
        if (!withdraw(from, amount)) {
            return false;
        }
        deposit(to, amount);
        profiles.save(from);
        profiles.save(to);
        return true;
    }

    public String format(double amount) {
        return symbol + format.format(amount);
    }

    public String symbol() {
        return symbol;
    }
}
