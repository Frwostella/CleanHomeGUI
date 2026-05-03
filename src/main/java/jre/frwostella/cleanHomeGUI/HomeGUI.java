package jre.frwostella.cleanHomeGUI;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public final class HomeGUI extends JavaPlugin implements Listener, TabCompleter {

    private final Map<UUID, BukkitTask> teleportTasks = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> renaming = new HashMap<>();

    private FileConfiguration messages;
    private FileConfiguration backups;
    private File backupsFile;

    private Connection database;

    private static final int MAX_HOMES = 3;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupMessages();
        setupBackups();
        setupDatabase();

        Bukkit.getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("home")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("sethome")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("delhome")).setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : teleportTasks.values()) {
            task.cancel();
        }

        teleportTasks.clear();
        cooldowns.clear();
        renaming.clear();

        closeDatabase();
    }

    private void setupMessages() {
        File file = new File(getDataFolder(), "messages.yml");

        if (!file.exists()) {
            saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(file);
    }

    private void setupBackups() {
        backupsFile = new File(getDataFolder(), "backups.yml");

        if (!backupsFile.exists()) {
            try {
                backupsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        backups = YamlConfiguration.loadConfiguration(backupsFile);
    }

    private void saveBackups() {
        try {
            backups.save(backupsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupDatabase() {
        try {
            String host = getConfig().getString("database.host", "localhost");
            int port = getConfig().getInt("database.port", 3306);
            String dbName = getConfig().getString("database.database", "cleanhomegui");
            String username = getConfig().getString("database.username", "root");
            String password = getConfig().getString("database.password", "");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + dbName
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

            database = DriverManager.getConnection(url, username, password);

            try (Statement statement = database.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS home_history (
                            id INT AUTO_INCREMENT PRIMARY KEY,
                            player_uuid VARCHAR(36) NOT NULL,
                            player_name VARCHAR(32) NOT NULL,
                            home_number INT NOT NULL,
                            action VARCHAR(32) NOT NULL,
                            home_name VARCHAR(255),
                            world VARCHAR(255),
                            x DOUBLE,
                            y DOUBLE,
                            z DOUBLE,
                            yaw DOUBLE,
                            pitch DOUBLE,
                            time BIGINT
                        )
                        """);
            }

            getLogger().info("Connected to MySQL database.");

        } catch (SQLException e) {
            getLogger().severe("Could not connect to MySQL database!");
            e.printStackTrace();
        }
    }

    private void closeDatabase() {
        try {
            if (database != null && !database.isClosed()) {
                database.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isDatabaseConnected() {
        try {
            return database != null && !database.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (cmd.getName().equalsIgnoreCase("home")) {

            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("cleanhomegui.reload")) {
                    sender.sendMessage(msg("no-permission"));
                    return true;
                }

                reloadConfig();
                setupMessages();
                setupBackups();
                closeDatabase();
                setupDatabase();

                sender.sendMessage(msg("reload-success"));
                return true;
            }

            if (args.length >= 1 && (
                    args[0].equalsIgnoreCase("restoredb")
                            || args[0].equalsIgnoreCase("backups")
                            || args[0].equalsIgnoreCase("history")
                            || args[0].equalsIgnoreCase("restore")
            )) {
                if (!sender.hasPermission("cleanhomegui.backup")) {
                    sender.sendMessage(msg("no-permission"));
                    return true;
                }

                if (args[0].equalsIgnoreCase("backups")) {
                    if (args.length != 2) {
                        sender.sendMessage(color("&cUsage: /home backups <player>"));
                        return true;
                    }

                    showBackups(sender, args[1]);
                    return true;
                }

                if (args[0].equalsIgnoreCase("history")) {
                    if (args.length != 2) {
                        sender.sendMessage(color("&cUsage: /home history <player>"));
                        return true;
                    }

                    showDatabaseHistory(sender, args[1]);
                    return true;
                }

                if (args[0].equalsIgnoreCase("restore")) {
                    if (args.length != 4) {
                        sender.sendMessage(color("&cUsage: /home restore <player> <homeNumber> <backupNumber>"));
                        return true;
                    }

                    Integer homeNumber = parse(args[2]);
                    Integer backupNumber = parsePositive(args[3]);

                    if (homeNumber == null || backupNumber == null) {
                        sender.sendMessage(color("&cUsage: /home restore <player> <homeNumber> <backupNumber>"));
                        return true;
                    }

                    restoreBackup(sender, args[1], homeNumber, backupNumber);
                    return true;
                }

                if (args[0].equalsIgnoreCase("restoredb")) {
                    if (args.length != 4) {
                        sender.sendMessage(color("&cUsage: /home restoredb <player> <homeNumber> <historyId>"));
                        return true;
                    }

                    Integer homeNumber = parse(args[2]);
                    Integer historyId = parsePositive(args[3]);

                    if (homeNumber == null || historyId == null) {
                        sender.sendMessage(color("&cUsage: /home restoredb <player> <homeNumber> <historyId>"));
                        return true;
                    }

                    restoreFromDatabase(sender, args[1], homeNumber, historyId);
                    return true;
                }
            }

            if (!(sender instanceof Player p)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            if (args.length == 0) {
                openGUI(p);
                return true;
            }

            Integer home = parse(args[0]);

            if (home == null) {
                p.sendMessage(msg("invalid-home"));
                villagerNo(p);
                return true;
            }

            teleport(p, home);
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("sethome")) {
            if (args.length != 1) {
                p.sendMessage(msg("sethome-usage"));
                villagerNo(p);
                return true;
            }

            Integer set = parse(args[0]);

            if (set == null) {
                p.sendMessage(msg("invalid-home"));
                villagerNo(p);
                return true;
            }

            setHome(p, set);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("delhome")) {
            if (args.length != 1) {
                p.sendMessage(msg("delhome-usage"));
                villagerNo(p);
                return true;
            }

            Integer del = parse(args[0]);

            if (del == null) {
                p.sendMessage(msg("invalid-home"));
                villagerNo(p);
                return true;
            }

            deleteHome(p, del);
            return true;
        }

        return true;
    }

    private void openGUI(Player p) {
        String title = color(getConfig().getString("gui.title", "&8Homes"));
        int size = getConfig().getInt("gui.size", 27);

        if (size < 27 || size > 54 || size % 9 != 0) {
            size = 27;
        }

        Inventory inv = Bukkit.createInventory(null, size, title);

        for (int i = 1; i <= MAX_HOMES; i++) {
            Location loc = getHome(p, i);
            String homeName = getHomeName(p, i);

            ItemStack bed = new ItemStack(loc == null ? Material.GRAY_BED : Material.BLUE_BED);
            ItemMeta bedMeta = bed.getItemMeta();

            if (loc == null) {
                bedMeta.setDisplayName(color(getConfig()
                        .getString("gui.empty-home-name-format", "&cHome {number} &7(Empty)")
                        .replace("{number}", String.valueOf(i))
                        .replace("{name}", homeName)));

                bedMeta.setLore(colorList(getConfig().getStringList("gui.empty-lore")));
            } else {
                bedMeta.setDisplayName(color(getConfig()
                        .getString("gui.home-name-format", "&9{name}")
                        .replace("{number}", String.valueOf(i))
                        .replace("{name}", homeName)));

                List<String> lore = new ArrayList<>(getConfig().getStringList("gui.rename-instructions"));

                if (lore.isEmpty()) {
                    lore.add("&7Right-click to rename");
                    lore.add("&7Left-click to teleport");
                }

                lore.add("");
                lore.add("&fWorld: &b" + loc.getWorld().getName());
                lore.add("&fX: &b" + loc.getBlockX());
                lore.add("&fY: &b" + loc.getBlockY());
                lore.add("&fZ: &b" + loc.getBlockZ());

                bedMeta.setLore(colorList(lore));
            }

            bed.setItemMeta(bedMeta);

            ItemStack delete = new ItemStack(loc == null ? Material.GRAY_DYE : Material.RED_DYE);
            ItemMeta deleteMeta = delete.getItemMeta();

            if (loc == null) {
                deleteMeta.setDisplayName(color("&7Delete Home " + i));
                deleteMeta.setLore(colorList(List.of("&cNo home set.")));
            } else {
                deleteMeta.setDisplayName(color(getConfig()
                        .getString("gui.delete-button-name", "&cDelete {name}")
                        .replace("{number}", String.valueOf(i))
                        .replace("{name}", homeName)));

                List<String> deleteLore = getConfig().getStringList("gui.delete-lore");

                if (deleteLore.isEmpty()) {
                    deleteLore = List.of("&7Click to delete this home.");
                }

                deleteMeta.setLore(colorList(deleteLore));
            }

            delete.setItemMeta(deleteMeta);

            int bedSlot = switch (i) {
                case 1 -> 10;
                case 2 -> 13;
                case 3 -> 16;
                default -> 13;
            };

            inv.setItem(bedSlot, bed);
            inv.setItem(bedSlot + 9, delete);
        }

        p.openInventory(inv);
        click(p);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = color(getConfig().getString("gui.title", "&8Homes"));
        if (!e.getView().getTitle().equals(title)) return;

        e.setCancelled(true);

        int slot = e.getRawSlot();

        int home = switch (slot) {
            case 10, 19 -> 1;
            case 13, 22 -> 2;
            case 16, 25 -> 3;
            default -> -1;
        };

        if (home == -1) return;

        if (slot == 19 || slot == 22 || slot == 25) {
            click(p);
            deleteHome(p, home);
            p.closeInventory();
            return;
        }

        if (e.isRightClick()) {
            click(p);
            renaming.put(p.getUniqueId(), home);
            p.closeInventory();
            p.sendMessage(msg("rename-prompt").replace("{home}", String.valueOf(home)));
            return;
        }

        if (getHome(p, home) == null) {
            setHome(p, home);
        } else {
            click(p);
            teleport(p, home);
        }

        p.closeInventory();
    }

    @EventHandler
    public void onRenameChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!renaming.containsKey(uuid)) return;

        e.setCancelled(true);

        int home = renaming.remove(uuid);
        String newName = e.getMessage();

        Bukkit.getScheduler().runTask(this, () -> {
            String path = "homes." + uuid + "." + home;

            getConfig().set(path + ".name", newName);
            saveConfig();

            p.sendMessage(msg("home-renamed")
                    .replace("{home}", String.valueOf(home))
                    .replace("{name}", newName));

            click(p);
        });
    }

    private void setHome(Player p, int n) {
        if (getHome(p, n) != null) {
            saveYamlBackup(p.getUniqueId(), p.getName(), n, "overwritten");
            saveDatabaseHistory(p.getUniqueId(), p.getName(), n, "overwritten");
        }

        Location loc = p.getLocation();
        String path = "homes." + p.getUniqueId() + "." + n;

        getConfig().set(path + ".world", loc.getWorld().getName());
        getConfig().set(path + ".x", loc.getX());
        getConfig().set(path + ".y", loc.getY());
        getConfig().set(path + ".z", loc.getZ());
        getConfig().set(path + ".yaw", loc.getYaw());
        getConfig().set(path + ".pitch", loc.getPitch());

        if (!getConfig().contains(path + ".name")) {
            getConfig().set(path + ".name", getHomeName(p, n));
        }

        saveConfig();
        saveDatabaseHistory(p.getUniqueId(), p.getName(), n, "set");

        p.sendMessage(msg("home-set").replace("{home}", String.valueOf(n)));
        click(p);
    }

    private void deleteHome(Player p, int n) {
        String path = "homes." + p.getUniqueId() + "." + n;

        if (!getConfig().contains(path + ".world")) {
            p.sendMessage(msg("home-not-set"));
            villagerNo(p);
            return;
        }

        saveYamlBackup(p.getUniqueId(), p.getName(), n, "deleted");
        saveDatabaseHistory(p.getUniqueId(), p.getName(), n, "deleted");

        getConfig().set(path, null);
        saveConfig();

        p.sendMessage(msg("home-deleted").replace("{home}", String.valueOf(n)));
    }

    private void saveYamlBackup(UUID uuid, String playerName, int n, String reason) {
        String homePath = "homes." + uuid + "." + n;

        if (!getConfig().contains(homePath + ".world")) return;

        long time = System.currentTimeMillis();
        String backupPath = "backups." + uuid + "." + n + "." + time;

        backups.set(backupPath + ".player-name", playerName);
        backups.set(backupPath + ".reason", reason);
        backups.set(backupPath + ".name", getConfig().getString(homePath + ".name", "Home " + n));
        backups.set(backupPath + ".world", getConfig().getString(homePath + ".world"));
        backups.set(backupPath + ".x", getConfig().getDouble(homePath + ".x"));
        backups.set(backupPath + ".y", getConfig().getDouble(homePath + ".y"));
        backups.set(backupPath + ".z", getConfig().getDouble(homePath + ".z"));
        backups.set(backupPath + ".yaw", getConfig().getDouble(homePath + ".yaw"));
        backups.set(backupPath + ".pitch", getConfig().getDouble(homePath + ".pitch"));

        saveBackups();
    }

    private void saveDatabaseHistory(UUID uuid, String playerName, int n, String action) {
        if (!isDatabaseConnected()) return;

        String path = "homes." + uuid + "." + n;

        try (PreparedStatement statement = database.prepareStatement("""
                INSERT INTO home_history
                (player_uuid, player_name, home_number, action, home_name, world, x, y, z, yaw, pitch, time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {

            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setInt(3, n);
            statement.setString(4, action);
            statement.setString(5, getConfig().getString(path + ".name", "Home " + n));
            statement.setString(6, getConfig().getString(path + ".world"));
            statement.setDouble(7, getConfig().getDouble(path + ".x"));
            statement.setDouble(8, getConfig().getDouble(path + ".y"));
            statement.setDouble(9, getConfig().getDouble(path + ".z"));
            statement.setDouble(10, getConfig().getDouble(path + ".yaw"));
            statement.setDouble(11, getConfig().getDouble(path + ".pitch"));
            statement.setLong(12, System.currentTimeMillis());

            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showDatabaseHistory(CommandSender sender, String playerName) {
        if (!isDatabaseConnected()) {
            sender.sendMessage(color("&cDatabase is not connected."));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        try (PreparedStatement statement = database.prepareStatement("""
                SELECT id, home_number, action, home_name, world, x, y, z, time
                FROM home_history
                WHERE player_uuid = ?
                ORDER BY id DESC
                LIMIT 20
                """)) {

            statement.setString(1, target.getUniqueId().toString());
            ResultSet rs = statement.executeQuery();

            sender.sendMessage(color("&8&m-------------------------"));
            sender.sendMessage(color("&bMySQL History for &f" + playerName));

            boolean found = false;

            while (rs.next()) {
                found = true;

                sender.sendMessage(color(
                        "&7ID &e" + rs.getInt("id") +
                                " &8| &fHome " + rs.getInt("home_number") +
                                " &8| &c" + rs.getString("action") +
                                " &8| &b" + rs.getString("world") +
                                " " + rs.getInt("x") +
                                " " + rs.getInt("y") +
                                " " + rs.getInt("z") +
                                " &8| &f" + rs.getString("home_name")
                ));
            }

            if (!found) {
                sender.sendMessage(color("&cNo database history found."));
            }

            sender.sendMessage(color("&7Restore: &f/home restoredb " + playerName + " <home> <historyId>"));
            sender.sendMessage(color("&8&m-------------------------"));

        } catch (SQLException e) {
            e.printStackTrace();
            sender.sendMessage(color("&cDatabase error."));
        }
    }

    private void restoreFromDatabase(CommandSender sender, String playerName, int homeNumber, int historyId) {
        if (!isDatabaseConnected()) {
            sender.sendMessage(color("&cDatabase is not connected."));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = target.getUniqueId();

        try (PreparedStatement statement = database.prepareStatement("""
                SELECT * FROM home_history
                WHERE id = ? AND player_uuid = ?
                """)) {

            statement.setInt(1, historyId);
            statement.setString(2, uuid.toString());

            ResultSet rs = statement.executeQuery();

            if (!rs.next()) {
                sender.sendMessage(color("&cNo matching history record found."));
                return;
            }

            String homePath = "homes." + uuid + "." + homeNumber;

            getConfig().set(homePath + ".name", rs.getString("home_name"));
            getConfig().set(homePath + ".world", rs.getString("world"));
            getConfig().set(homePath + ".x", rs.getDouble("x"));
            getConfig().set(homePath + ".y", rs.getDouble("y"));
            getConfig().set(homePath + ".z", rs.getDouble("z"));
            getConfig().set(homePath + ".yaw", rs.getDouble("yaw"));
            getConfig().set(homePath + ".pitch", rs.getDouble("pitch"));

            saveConfig();

            saveDatabaseHistory(uuid, playerName, homeNumber, "restored");

            sender.sendMessage(color("&aRestored Home " + homeNumber + " for " + playerName + " from MySQL ID " + historyId + "."));

        } catch (SQLException e) {
            e.printStackTrace();
            sender.sendMessage(color("&cDatabase error."));
        }
    }

    private void showBackups(CommandSender sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = target.getUniqueId();

        sender.sendMessage(color("&8&m-------------------------"));
        sender.sendMessage(color("&bYAML Backups for &f" + playerName));

        boolean found = false;

        for (int home = 1; home <= MAX_HOMES; home++) {
            List<String> keys = getBackupKeys(uuid, home);

            if (keys.isEmpty()) continue;

            found = true;
            sender.sendMessage(color("&eHome " + home + ":"));

            int index = 1;

            for (String key : keys) {
                String path = "backups." + uuid + "." + home + "." + key;

                sender.sendMessage(color("&7#" + index +
                        " &f" + backups.getString(path + ".name", "Home " + home) +
                        " &8- &c" + backups.getString(path + ".reason", "unknown") +
                        " &8- &b" + backups.getString(path + ".world", "unknown") +
                        " " + backups.getInt(path + ".x") +
                        " " + backups.getInt(path + ".y") +
                        " " + backups.getInt(path + ".z")));

                index++;
            }
        }

        if (!found) {
            sender.sendMessage(color("&cNo backups found."));
        }

        sender.sendMessage(color("&7Restore: &f/home restore " + playerName + " <home> <backupNumber>"));
        sender.sendMessage(color("&8&m-------------------------"));
    }

    private void restoreBackup(CommandSender sender, String playerName, int home, int backupNumber) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = target.getUniqueId();

        List<String> keys = getBackupKeys(uuid, home);

        if (keys.isEmpty()) {
            sender.sendMessage(color("&cNo backups found for that home."));
            return;
        }

        if (backupNumber < 1 || backupNumber > keys.size()) {
            sender.sendMessage(color("&cInvalid backup number."));
            return;
        }

        String key = keys.get(backupNumber - 1);
        String backupPath = "backups." + uuid + "." + home + "." + key;
        String homePath = "homes." + uuid + "." + home;

        getConfig().set(homePath + ".name", backups.getString(backupPath + ".name", "Home " + home));
        getConfig().set(homePath + ".world", backups.getString(backupPath + ".world"));
        getConfig().set(homePath + ".x", backups.getDouble(backupPath + ".x"));
        getConfig().set(homePath + ".y", backups.getDouble(backupPath + ".y"));
        getConfig().set(homePath + ".z", backups.getDouble(backupPath + ".z"));
        getConfig().set(homePath + ".yaw", backups.getDouble(backupPath + ".yaw"));
        getConfig().set(homePath + ".pitch", backups.getDouble(backupPath + ".pitch"));

        saveConfig();

        sender.sendMessage(color("&aRestored Home " + home + " for " + playerName + " from YAML backup #" + backupNumber + "."));
    }

    private List<String> getBackupKeys(UUID uuid, int home) {
        ConfigurationSection section = backups.getConfigurationSection("backups." + uuid + "." + home);

        if (section == null) return new ArrayList<>();

        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort((a, b) -> Long.compare(Long.parseLong(b), Long.parseLong(a)));

        return keys;
    }

    private void teleport(Player p, int n) {
        Location home = getHome(p, n);

        if (home == null) {
            p.sendMessage(msg("home-not-set"));
            villagerNo(p);
            return;
        }

        UUID uuid = p.getUniqueId();

        if (teleportTasks.containsKey(uuid)) {
            p.sendMessage(msg("already-teleporting"));
            villagerNo(p);
            return;
        }

        int cooldownSeconds = getConfig().getInt("cooldown-seconds", 30);

        if (cooldownSeconds > 0 && cooldowns.containsKey(uuid)) {
            long remaining = (cooldowns.get(uuid) - System.currentTimeMillis()) / 1000;

            if (remaining > 0) {
                p.sendMessage(msg("cooldown").replace("{time}", String.valueOf(remaining)));
                villagerNo(p);
                return;
            }
        }

        int delay = Math.max(0, getConfig().getInt("teleport-delay-seconds", 5));

        p.sendMessage(msg("teleport-start"));

        if (delay == 0) {
            p.teleport(home);
            playSound(p, "entity.ender_pearl.throw");
            p.sendMessage(msg("teleported").replace("{home}", String.valueOf(n)));

            if (cooldownSeconds > 0) {
                cooldowns.put(uuid, System.currentTimeMillis() + cooldownSeconds * 1000L);
            }

            return;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {

            int timeLeft = delay;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancelTeleport(uuid);
                    return;
                }

                if (timeLeft <= 0) {
                    p.teleport(home);
                    playSound(p, "entity.ender_pearl.throw");
                    p.sendMessage(msg("teleported").replace("{home}", String.valueOf(n)));

                    int currentCooldown = getConfig().getInt("cooldown-seconds", 30);

                    if (currentCooldown > 0) {
                        cooldowns.put(uuid, System.currentTimeMillis() + currentCooldown * 1000L);
                    }

                    cancelTeleport(uuid);
                    return;
                }

                p.sendActionBar(Component.text(msg("actionbar-countdown")
                        .replace("{time}", String.valueOf(timeLeft))));

                playSound(p, "block.note_block.pling");

                timeLeft--;
            }

        }, 0L, 20L);

        teleportTasks.put(uuid, task);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!teleportTasks.containsKey(uuid)) return;
        if (e.getTo() == null) return;

        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {

            cancelTeleport(uuid);
            p.sendMessage(msg("teleport-cancelled"));
            villagerNo(p);
        }
    }

    private void cancelTeleport(UUID uuid) {
        BukkitTask task = teleportTasks.remove(uuid);

        if (task != null) {
            task.cancel();
        }
    }

    private Location getHome(Player p, int n) {
        String path = "homes." + p.getUniqueId() + "." + n;

        if (!getConfig().contains(path + ".world")) return null;

        World world = Bukkit.getWorld(getConfig().getString(path + ".world"));

        if (world == null) return null;

        return new Location(
                world,
                getConfig().getDouble(path + ".x"),
                getConfig().getDouble(path + ".y"),
                getConfig().getDouble(path + ".z"),
                (float) getConfig().getDouble(path + ".yaw"),
                (float) getConfig().getDouble(path + ".pitch")
        );
    }

    private String getHomeName(Player p, int n) {
        String savedName = getConfig().getString("homes." + p.getUniqueId() + "." + n + ".name");

        if (savedName != null) {
            return savedName;
        }

        return getConfig().getString("default-home-names." + n, "Home " + n);
    }

    private Integer parse(String input) {
        try {
            int number = Integer.parseInt(input);
            return number >= 1 && number <= MAX_HOMES ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parsePositive(String input) {
        try {
            int number = Integer.parseInt(input);
            return number >= 1 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String msg(String path) {
        return color(messages.getString(path, "&cMissing message: " + path));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private List<String> colorList(List<String> list) {
        List<String> colored = new ArrayList<>();

        for (String line : list) {
            colored.add(color(line));
        }

        return colored;
    }

    private void click(Player p) {
        playSound(p, "ui.button.click");
    }

    private void villagerNo(Player p) {
        playSound(p, "entity.villager.no");
    }

    private void playSound(Player p, String sound) {
        p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("home")) {

            if (args.length == 1) {
                completions.add("1");
                completions.add("2");
                completions.add("3");

                if (sender.hasPermission("cleanhomegui.reload")) {
                    completions.add("reload");
                }

                if (sender.hasPermission("cleanhomegui.backup")) {
                    completions.add("backups");
                    completions.add("history");
                    completions.add("restore");
                    completions.add("restoredb");
                }

                return filter(completions, args[0]);
            }

            if (args.length == 2) {
                if (sender.hasPermission("cleanhomegui.backup")
                        && (args[0].equalsIgnoreCase("backups")
                        || args[0].equalsIgnoreCase("history")
                        || args[0].equalsIgnoreCase("restore")
                        || args[0].equalsIgnoreCase("restoredb"))) {

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        completions.add(player.getName());
                    }

                    return filter(completions, args[1]);
                }
            }

            if (args.length == 3) {
                if (sender.hasPermission("cleanhomegui.backup")
                        && (args[0].equalsIgnoreCase("restore")
                        || args[0].equalsIgnoreCase("restoredb"))) {

                    completions.add("1");
                    completions.add("2");
                    completions.add("3");

                    return filter(completions, args[2]);
                }
            }

            if (args.length == 4) {
                if (sender.hasPermission("cleanhomegui.backup")
                        && (args[0].equalsIgnoreCase("restore")
                        || args[0].equalsIgnoreCase("restoredb"))) {

                    completions.add("1");
                    completions.add("2");
                    completions.add("3");
                    completions.add("4");
                    completions.add("5");
                    completions.add("10");

                    return filter(completions, args[3]);
                }
            }
        }

        if (command.getName().equalsIgnoreCase("sethome")
                || command.getName().equalsIgnoreCase("delhome")) {

            if (args.length == 1) {
                completions.add("1");
                completions.add("2");
                completions.add("3");

                return filter(completions, args[0]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String input) {
        List<String> result = new ArrayList<>();

        for (String option : list) {
            if (option.toLowerCase().startsWith(input.toLowerCase())) {
                result.add(option);
            }
        }

        return result;
    }
}