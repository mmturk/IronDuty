package dev.mmturk.ironduty.ironDuty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class IronDuty extends JavaPlugin implements CommandExecutor {

    private static final Map<UUID, LocalDateTime> dutyPlayers = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("duty").setExecutor(this);
        getCommand("unduty").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("ironduty.use")) {
            player.sendMessage("§cYou don't have permission.");
            return true;
        }

        FileConfiguration config = getConfig();
        List<String> allowedGroups = config.getStringList("Staff-luckperm-groups");
        String playerGroup = getPrimaryGroup(player);

        if (!allowedGroups.contains(playerGroup)) {
            player.sendMessage("§cYou are not allowed to use /" + label.toLowerCase());
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("duty")) {
            if (dutyPlayers.containsKey(player.getUniqueId())) {
                player.sendMessage("§eYou are already on duty.");
                return true;
            }

            dutyPlayers.put(player.getUniqueId(), LocalDateTime.now());

            String colorName = config.getString("Glow-Colors." + playerGroup, "WHITE");
            ChatColor glowColor = ChatColor.valueOf(colorName.toUpperCase(Locale.ROOT));

            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(player.getName());
            if (team == null) {
                team = scoreboard.registerNewTeam(player.getName());
            }
            team.setColor(glowColor);
            team.addEntry(player.getName());

            player.setGlowing(true);

            Bukkit.broadcastMessage("§a" + player.getName() + " went on duty!");
            sendWebhook(player.getName() + " went **ON DUTY** at " + now());

        } else if (cmd.getName().equalsIgnoreCase("unduty")) {
            if (!dutyPlayers.containsKey(player.getUniqueId())) {
                player.sendMessage("§eYou are not on duty.");
                return true;
            }

            LocalDateTime start = dutyPlayers.remove(player.getUniqueId());
            LocalDateTime end = LocalDateTime.now();

            player.setGlowing(false);

            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(player.getName());
            if (team != null) {
                team.removeEntry(player.getName());
            }

            Bukkit.broadcastMessage("§c" + player.getName() + " went off duty!");

            String duration = formatDuration(Duration.between(start, end));
            String message = player.getName() + " went **OFF DUTY**\nFrom: " + format(start) + "\nTo: " + format(end) + "\nDuration: " + duration;
            sendWebhook(message);
        }

        return true;
    }

    private String getPrimaryGroup(Player player) {

        return "Admin";
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String format(LocalDateTime dt) {
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void sendWebhook(String message) {
        try {
            String webhookURL = getConfig().getString("DiscordWebhookURL");
            if (webhookURL == null || webhookURL.isEmpty()) {
                getLogger().warning("Webhook URL is not set in config.");
                return;
            }

            URL url = new URL(webhookURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            String payload = "{\"content\":\"" + message.replace("\"", "\\\"") + "\"}";
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes());
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204 && responseCode != 200) {
                getLogger().warning("Failed to send webhook, response code: " + responseCode);
            }

        } catch (Exception e) {
            getLogger().warning("Error sending webhook: " + e.getMessage());
        }
    }
}
