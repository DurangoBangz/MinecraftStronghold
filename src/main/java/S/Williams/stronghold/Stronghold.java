package S.Williams.stronghold;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.attribute.Attribute;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.FireworkEffect;
import org.bukkit.Color;
import org.bukkit.block.BlockFace; // Ensure this is imported
import org.bukkit.block.data.Directional; // Import the Directional interface
import java.util.Map;
import java.util.HashMap;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.Vector;


public class Stronghold extends JavaPlugin implements Listener, CommandExecutor {

    private Location strongholdCenter;
    private boolean isPlayerControlled = true; // Indicates stronghold ownership
    private boolean siegeActive = false; // Indicates siege is Active
    private final int RADIUS = 80; // Radius for the flat area
    private final int[] RINGS = {25, 40, 60}; // Radii for each ring
    private final int BANNER_SPACING = 15; // Distance between banners on the perimeter
    private final Material BANNER_MATERIAL = Material.BLUE_BANNER; // Default banner material
    private List<Location> bannerLocations = new ArrayList<>(); // To track banner locations
    private final Map<Location, Material> originalTerrain = new HashMap<>();

    // banner stuff

    private void placeBanners(Location center) {
        World world = center.getWorld();
        int radius = RINGS[RINGS.length - 1]; // Outer ring radius

        // Clear any existing banners
        for (Location bannerLoc : bannerLocations) {
            if (bannerLoc.getBlock().getType() == BANNER_MATERIAL) {
                bannerLoc.getBlock().setType(Material.AIR);
            }
        }
        bannerLocations.clear();

        // Place banners at intervals
        for (int degree = 0; degree < 360; degree += BANNER_SPACING) {
            double radians = Math.toRadians(degree);
            double x = center.getX() + (radius + 3) * Math.cos(radians); // Move outward by 3 blocks
            double z = center.getZ() + (radius + 3) * Math.sin(radians); // Move outward by 3 blocks
            Location bannerLoc = new Location(world, x, center.getY() + 1, z); // Move up by 1 block

            // Place the banner
            bannerLoc.getBlock().setType(BANNER_MATERIAL);

            // Set the banner's facing direction
            if (bannerLoc.getBlock().getBlockData() instanceof Directional directional) {
                directional.setFacing(getBannerFacing(center, bannerLoc));
                bannerLoc.getBlock().setBlockData(directional);
            }

            bannerLocations.add(bannerLoc);
        }
    }

    // Helper method to determine the banner's facing direction
    private BlockFace getBannerFacing(Location center, Location bannerLoc) {
        double dx = bannerLoc.getX() - center.getX();
        double dz = bannerLoc.getZ() - center.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));

        // Convert angle to 0-360 degrees
        angle = (angle + 360) % 360;

        // Determine the closest BlockFace
        if (angle >= 45 && angle < 135) return BlockFace.SOUTH;
        if (angle >= 135 && angle < 225) return BlockFace.WEST;
        if (angle >= 225 && angle < 315) return BlockFace.NORTH;
        return BlockFace.EAST;
    }



    // Update banner colors based on ownership
    private void updateBannerColors(boolean isPlayerOwned) {
        Material newBannerMaterial = isPlayerOwned ? Material.BLUE_BANNER : Material.RED_BANNER;

        for (Location bannerLoc : bannerLocations) {
            // Replace the banner with the new material
            if (bannerLoc.getBlock().getType() == Material.BLUE_BANNER || bannerLoc.getBlock().getType() == Material.RED_BANNER) {
                bannerLoc.getBlock().setType(newBannerMaterial);

                // Set the banner's facing direction (preserve orientation)
                if (bannerLoc.getBlock().getBlockData() instanceof Directional directional) {
                    directional.setFacing(getBannerFacing(strongholdCenter, bannerLoc));
                    bannerLoc.getBlock().setBlockData(directional);
                }
            }
        }
    }



    // Fireworks effect on player victory
    private void launchFireworks(Location center) {
        World world = center.getWorld();
        int radius = RINGS[RINGS.length - 1]; // Outer ring radius

        // Schedule repeating fireworks
        new BukkitRunnable() {
            int fireworksCount = 0; // Counter for the number of fireworks launched

            @Override
            public void run() {
                if (fireworksCount >= 20) { // Stop after 20 fireworks
                    cancel();
                    return;
                }

                // Launch a firework at a random position around the stronghold
                double angle = Math.toRadians(new Random().nextInt(360));
                double x = center.getX() + (radius + 3) * Math.cos(angle); // Add 3 to radius for fireworks outside
                double z = center.getZ() + (radius + 3) * Math.sin(angle);
                Location fireworkLoc = new Location(world, x, center.getY() + 2, z);

                Firework firework = world.spawn(fireworkLoc, Firework.class);
                FireworkMeta fireworkMeta = firework.getFireworkMeta();

                // Create random firework effects
                fireworkMeta.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.values()[new Random().nextInt(FireworkEffect.Type.values().length)]) // Random firework type
                        .withColor(getRandomColors())
                        .withFade(getRandomColors())
                        .withTrail()
                        .withFlicker()
                        .build());
                fireworkMeta.setPower(1 + new Random().nextInt(2)); // Random power between 1 and 2
                firework.setFireworkMeta(fireworkMeta);

                fireworksCount++;
            }
        }.runTaskTimer(this, 0, 10L); // Launch fireworks every 10 ticks (0.5 seconds)
    }

    // Helper method to generate random firework colors
    private List<Color> getRandomColors() {
        List<Color> colors = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 3; i++) { // Add up to 3 random colors
            colors.add(Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
        }
        return colors;
    }


    // start and close of plugin
    @Override
    public void onEnable() {
        getLogger().info("StrongHold has started!");
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("setstronghold").setExecutor(this);
        this.getCommand("deletestrongholds").setExecutor(this); // Register the delete command
    }
    @Override
    public void onDisable() {
        getLogger().info("StrongHold has stopped!");
    }


    // set stronghold command and deletestronghold command
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setstronghold")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                strongholdCenter = player.getLocation();
                flattenTerrain(strongholdCenter);
                isPlayerControlled = true; // Set ownership to players
                sender.sendMessage("Player stronghold set, terrain flattened, and control rings marked!");
                setBeaconColor(strongholdCenter, true); // Player-controlled by default

                // Automatically start the siege after 2 minutes
                startSiegeAfterDelay();

                return true;
            }
        } else if (command.getName().equalsIgnoreCase("deletestrongholds")) {
            if (strongholdCenter != null) {
                deleteStronghold();
                sender.sendMessage("All strongholds have been deleted.");
            } else {
                sender.sendMessage("No strongholds to delete.");
            }
            return true;
        }
        return false;
    }
    private void deleteStronghold() {
        World world = strongholdCenter.getWorld();
        for (int radius : RINGS) {
            for (int degree = 0; degree < 360; degree += 5) {
                double radians = Math.toRadians(degree);
                double x = strongholdCenter.getX() + radius * Math.cos(radians);
                double z = strongholdCenter.getZ() + radius * Math.sin(radians);
                Location blockLoc = new Location(world, x, strongholdCenter.getY(), z);
                blockLoc.getBlock().setType(Material.AIR);
            }
        }
        strongholdCenter = null;
        isPlayerControlled = false;
    }


    // initialize one stronghold
    private void createControlRings(Location center) {
        World world = center.getWorld();
        Material[] ringMaterials = {
                Material.YELLOW_CONCRETE, // Inner ring
                Material.ORANGE_CONCRETE,
                Material.RED_CONCRETE // Outer ring
        };

        for (int i = 0; i < RINGS.length; i++) {
            int radius = RINGS[i];
            Material material = ringMaterials[i % ringMaterials.length]; // Choose color based on ring index

            // Calculate the height adjustment for this ring
            int ringHeightOffset = -i;

            for (int degree = 0; degree < 360; degree += 5) { // Adjust 5 for denser block placement
                double radians = Math.toRadians(degree);
                double x = center.getX() + radius * Math.cos(radians);
                double z = center.getZ() + radius * Math.sin(radians);
                Location blockLoc = new Location(world, x, center.getY() + ringHeightOffset, z);

                // Set the block at this location to the ring material, flush with the calculated height
                blockLoc.getBlock().setType(material);
            }
        }
    }
    private void flattenTerrain(Location center) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int centerY = center.getBlockY();

        // Flatten terrain in a larger circle around the center
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double distance = Math.sqrt(x * x + z * z);

                if (distance <= RADIUS) {
                    // Determine which ring the block belongs to
                    int ringLevel = 0;
                    for (int i = 0; i < RINGS.length; i++) {
                        if (distance <= RINGS[i]) {
                            ringLevel = i;
                            break;
                        }
                    }

                    // Calculate the height for this ring
                    int blockY = centerY - ringLevel;

                    // Save original terrain state
                    Location blockLoc = new Location(world, centerX + x, blockY, centerZ + z);
                    originalTerrain.put(blockLoc.clone(), blockLoc.getBlock().getType());

                    // Set the terrain for the current block
                    blockLoc.getBlock().setType(Material.GRASS_BLOCK);

                    // Clear blocks above to make sure it's a flat surface
                    for (int y = blockY + 1; y <= centerY + 5; y++) {
                        Location airLoc = new Location(world, centerX + x, y, centerZ + z);
                        originalTerrain.put(airLoc.clone(), airLoc.getBlock().getType());
                        airLoc.getBlock().setType(Material.AIR);
                    }
                }
            }
        }

        // Display control rings with colored blocks
        createControlRings(center);
        // Place banners around the perimeter
        placeBanners(center);
    }



    // Time delay before mob siege
    private void startSiegeAfterDelay() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage("A mob siege has begun! Defend the stronghold!");
                siegeActive = true;

                // Cancel any previous timer
                if (siegeTimer != null) {
                    siegeTimer.cancel();
                }

                // Spawn mobs and get the list of spawned mobs
                List<Mob> mobs = spawnSiegeMobs();

                // Start the timer for the mob siege
                if (mobs != null) {
                    startSiegeTimer(mobs); // Timer will handle `endSiege` upon completion
                }
            }
        }.runTaskLater(this, 1200L); // Delay of 1 minute (1200 ticks)
    }

    // Mob Siege
    private void startSiegeTimer(List<Mob> mobs) {
        // Cancel the previous timer if it exists
        if (siegeTimer != null) {
            siegeTimer.cancel();
        }
        // Spawn TNT cannons
        spawnTNTCannons(strongholdCenter);

        siegeTimer = new BukkitRunnable() {
            int timeLeft = 60; // 60 seconds for the siege

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    // Timer ends without mob capture, stronghold defended
                    endSiege(mobs, true, true); // End the siege cleanly
                    siegeActive = false;
                    cancel();
                    return;
                }

                // Display the countdown timer in the Action Bar for all players
                String timeFormatted = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60); // Format as MM:SS
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar("§eSiege Time Remaining: §c" + timeFormatted);
                }

                timeLeft--;
            }
        };

        siegeTimer.runTaskTimer(this, 0, 20L); // Runs every second (20 ticks)
    }
    private List<Mob> spawnSiegeMobs() {
        if (!isPlayerControlled) return null; // Ensure stronghold is still player-controlled

        World world = strongholdCenter.getWorld();
        int spawnRadius = RINGS[RINGS.length - 1]; // Spawn mobs at the outermost ring
        List<Mob> mobs = new ArrayList<>();

        int maxMobs = 25; // Maintain a constant mob count

        // Initial spawning of mobs
        for (int i = 0; i < maxMobs; i++) {
            spawnMobAtRandomLocation(mobs, spawnRadius, world);
        }

        // Periodically check and replace dead or invalid mobs
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!siegeActive) {
                    cancel();
                    return;
                }

                // Remove invalid or dead mobs from the list
                mobs.removeIf(mob -> mob == null || !mob.isValid());

                // Replace missing mobs to maintain the count
                while (mobs.size() < maxMobs) {
                    spawnMobAtRandomLocation(mobs, spawnRadius, world);
                }

                // Ensure mobs are guided toward the center
                guideMobsToCenter(mobs);
            }
        }.runTaskTimer(this, 0, 20L); // Check and guide every second (20 ticks)

        return mobs; // Return the list of spawned mobs
    }
    private void spawnMobAtRandomLocation(List<Mob> mobs, int spawnRadius, World world) {
        double angle = Math.toRadians(new Random().nextInt(360));
        double x = strongholdCenter.getX() + spawnRadius * Math.cos(angle);
        double z = strongholdCenter.getZ() + spawnRadius * Math.sin(angle);
        Location spawnLocation = new Location(world, x, strongholdCenter.getY() + 4, z);

        // Spawn a Piglin
        Piglin piglin = (Piglin) world.spawnEntity(spawnLocation, EntityType.PIGLIN);
        piglin.setImmuneToZombification(true); // Make Piglin immune to zombification
        piglin.setPersistent(true); // Prevent despawning
        piglin.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0); // Set health
        piglin.setHealth(20.0);

        // Equip Piglin with armor
        piglin.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        piglin.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        piglin.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        piglin.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));

        // Equip Piglin with a weapon (randomly choose between sword and axe)
        ItemStack weapon = new ItemStack(new Random().nextBoolean() ? Material.IRON_SWORD : Material.IRON_AXE);
        piglin.getEquipment().setItemInMainHand(weapon);

        // Prevent Piglin from dropping items
        piglin.getEquipment().setHelmetDropChance(0);
        piglin.getEquipment().setChestplateDropChance(0);
        piglin.getEquipment().setLeggingsDropChance(0);
        piglin.getEquipment().setBootsDropChance(0);
        piglin.getEquipment().setItemInMainHandDropChance(0);

        mobs.add(piglin); // Add the Piglin to the mobs list
    }

    private void guideMobsToCenter(List<Mob> mobs) {
        for (Mob mob : mobs) {
            if (mob == null || !mob.isValid()) continue;

            Player nearestPlayer = getNearestPlayer(mob);

            if (nearestPlayer != null) {
                mob.setTarget(nearestPlayer); // Target the nearest player if within range
            } else {
                // Use pathfinder to move mob toward the stronghold center
                mob.getPathfinder().moveTo(strongholdCenter);
            }
        }

        // Check if any mob has captured the stronghold
        checkCaptureCondition(mobs);
    }
    private Player getNearestPlayer(Mob mob) {
        double detectionRange = 10.0; // Range in blocks within which mobs detect players
        Player nearestPlayer = null;
        double nearestDistance = detectionRange;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(mob.getWorld())) {
                double distance = player.getLocation().distance(mob.getLocation());
                if (distance <= nearestDistance) {
                    nearestPlayer = player;
                    nearestDistance = distance;
                }
            }
        }
        return nearestPlayer;
    }
    private BukkitRunnable siegeTimer;
    private void checkCaptureCondition(List<Mob> mobs) {
        int captureRadius = RINGS[0];
        int requiredMobs = 10;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!siegeActive) {
                    cancel();
                    if (defensiveCircleBar != null) {
                        defensiveCircleBar.removeAll(); // Remove the BossBar
                        defensiveCircleBar = null; // Reset BossBar reference
                    }
                    return;
                }

                int mobsInRadius = 0;

                for (Mob mob : mobs) {
                    if (mob != null && mob.isValid() && mob.getLocation().distance(strongholdCenter) <= captureRadius) {
                        mobsInRadius++;
                    }
                }

                updateDefensiveCircleBar(mobsInRadius, requiredMobs); // Update the BossBar

                if (mobsInRadius >= requiredMobs) {
                    if (siegeTimer != null) {
                        siegeTimer.cancel();
                    }
                    siegeActive = false;
                    endSiege(mobs, false, true);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0, 20L);
    }
    private BossBar defensiveCircleBar; // For defensive siege (mobs in circle)



    // Time delay before player siege
    private void startPlayerOffensiveSiege() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage("A player offensive siege has begun! Capture the stronghold!");
                siegeActive = true;
                isOffensiveSiege = true; // Mark as offensive siege
                List<Mob> defendingMobs = spawnDefendingMobs();

                // Ensure offensive kill bar is reset
                totalKills = 0;
                updateOffensiveKillBar(totalKills, requiredKills);

                // Start timer for the player offensive siege
                startPlayerSiegeTimer(defendingMobs);
            }
        }.runTaskLater(this, 1200L); // Delay of 1 minute (1200 ticks)
    }


    // new stuff
    private BossBar offensiveKillBar; // For offensive siege (mobs killed)
    private void updateOffensiveKillBar(int totalKills, int requiredKills) {
        if (offensiveKillBar == null) {
            offensiveKillBar = Bukkit.createBossBar("§cMobs Killed", BarColor.RED, BarStyle.SEGMENTED_10);
            for (Player player : Bukkit.getOnlinePlayers()) {
                offensiveKillBar.addPlayer(player);
            }
        }
        offensiveKillBar.setTitle("§cMobs Killed: " + totalKills + " / " + requiredKills);
        offensiveKillBar.setProgress(Math.min(1.0, (double) totalKills / requiredKills));
    }
    private void removeOffensiveKillBar() {
        if (offensiveKillBar != null) {
            offensiveKillBar.removeAll(); // Remove the BossBar from all players
            offensiveKillBar = null; // Nullify the reference
        }
    }

    private void updateTimeRemainingActionBar(int timeLeft) {
        String timeFormatted = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§eTime Remaining: §c" + timeFormatted));
        }
    }
    private void updateDefensiveCircleBar(int mobsInRadius, int requiredMobs) {
        if (defensiveCircleBar == null) {
            defensiveCircleBar = Bukkit.createBossBar("§eMobs in Circle", BarColor.YELLOW, BarStyle.SEGMENTED_10);
            for (Player player : Bukkit.getOnlinePlayers()) {
                defensiveCircleBar.addPlayer(player);
            }
        }
        defensiveCircleBar.setTitle("§eMobs in Circle: " + mobsInRadius + " / " + requiredMobs);
        defensiveCircleBar.setProgress(Math.min(1.0, (double) mobsInRadius / requiredMobs));
    }
    private boolean isOffensiveSiege = false; // Track if the current siege is offensive

    // Player Siege
    private void startPlayerSiegeTimer(List<Mob> defendingMobs) {
        siegeTimer = new BukkitRunnable() {
            int timeLeft = 120; // 120 seconds for the offensive siege

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    // Timer ends without player capture
                    removeBossMob(); // Ensure the boss is removed
                    siegeActive = false;
                    isOffensiveSiege = false;
                    endSiege(defendingMobs, false, false); // Players lose the offensive siege
                    cancel();
                    return;
                }

                // Update time remaining
                updateTimeRemainingActionBar(timeLeft);

                // Check if players have met the requirements
                checkPlayerCaptureCondition(defendingMobs);

                timeLeft--;
            }
        };

        siegeTimer.runTaskTimer(this, 0, 20L);
    }
    private List<Mob> spawnDefendingMobs() {
        World world = strongholdCenter.getWorld();
        int spawnRadius = RINGS[0]; // Spawn mobs inside the innermost ring
        List<Mob> mobs = new ArrayList<>();
        int maxMobs = 50; // Number of defending mobs

        // Initial spawning of mobs
        for (int i = 0; i < maxMobs; i++) {
            spawnMobAtRandomLocation(mobs, spawnRadius, world);
        }

        // Periodically check and replace dead or invalid mobs
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!siegeActive) {
                    cancel(); // Cancel if the stronghold is recaptured by players
                    return;
                }

                // Remove invalid or dead mobs from the list
                mobs.removeIf(mob -> mob == null || !mob.isValid());

                // Replace missing mobs to maintain the count
                while (mobs.size() < maxMobs) {
                    spawnMobAtRandomLocation(mobs, spawnRadius, world);
                }

                // Continuously update the action bar with kill count
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar("§eMobs Killed: §c" + totalKills + " / " + requiredKills);
                }
            }
        }.runTaskTimer(this, 0, 20L); // Check and replenish every second (20 ticks)

        return mobs;
    }
    private int totalKills = 0; // Track total kills across all players
    private final int requiredKills = 5; // Number of mobs players need to kill
    private void checkPlayerCaptureCondition(List<Mob> defendingMobs) {
        if (totalKills >= requiredKills) {
            spawnBossMob(defendingMobs); // Spawn the final boss mob
            totalKills = 0; // Reset the kill counter for the next phase

            removeOffensiveKillBar(); // Remove the kill bar when the boss spawns
        } else if (!bossSpawned) { // Only update if the boss hasn't spawned
            updateOffensiveKillBar(totalKills, requiredKills);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!siegeActive || !isOffensiveSiege) return; // Only run during active offensive siege

        if (event.getEntity().getKiller() != null && event.getEntity() instanceof Mob) { // Ensure a player killed the mob
            if (!bossSpawned) { // Only increment and update if the boss hasn't spawned
                totalKills++; // Increment global kill counter
                updateOffensiveKillBar(totalKills, requiredKills); // Update the BossBar
            }
        }
    }
    private BossBar bossBar; // Global variable to track the boss bar
    private boolean bossSpawned = false; // Track if the boss has been spawned
    private Mob bossMob; // Generalize to Mob to support Piglin Brute or other entities
    private void spawnBossMob(List<Mob> defendingMobs) {
        if (bossSpawned) return; // Prevent multiple boss spawns
        bossSpawned = true; // Mark boss as spawned

        World world = strongholdCenter.getWorld();
        Location spawnLocation = strongholdCenter.clone().add(0, 2, 0);

        // Spawn the Piglin Brute
        PiglinBrute bossMob = (PiglinBrute) world.spawnEntity(spawnLocation, EntityType.PIGLIN_BRUTE);
        bossMob.setImmuneToZombification(true); // Make Piglin Brute immune to zombification
        bossMob.setPersistent(true); // Prevent despawning
        bossMob.setCustomName("§cBoss Piglin Brute");
        bossMob.setCustomNameVisible(true);
        bossMob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(100.0);
        bossMob.setHealth(100.0);
        bossMob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(15.0); // Stronger attack

        // Equip boss with Netherite armor
        bossMob.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        bossMob.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        bossMob.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        bossMob.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        bossMob.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));

        // Prevent boss from dropping items
        bossMob.getEquipment().setHelmetDropChance(0);
        bossMob.getEquipment().setChestplateDropChance(0);
        bossMob.getEquipment().setLeggingsDropChance(0);
        bossMob.getEquipment().setBootsDropChance(0);
        bossMob.getEquipment().setItemInMainHandDropChance(0);

        this.bossMob = bossMob; // Store the reference to the boss mob

        // Create BossBar for the boss
        bossBar = Bukkit.createBossBar("§cBoss Piglin Brute", BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setProgress(1.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        // Update BossBar progress
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!bossMob.isValid()) {
                    cancel();
                    bossBar.removeAll();
                    endBossFight(defendingMobs);
                    return;
                }
                double progress = bossMob.getHealth() / bossMob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                bossBar.setProgress(progress);
            }
        }.runTaskTimer(this, 0, 20L);
    }

    private void endBossFight(List<Mob> defendingMobs) {
        Bukkit.broadcastMessage("§aThe Boss has been defeated! The stronghold is now player-controlled!");
        removeBossMob(); // Ensure the boss is removed
        siegeActive = false;
        endSiege(defendingMobs, true, false);
        if (siegeTimer != null) {
            siegeTimer.cancel();
        }
    }



    // Set Beacon color
    private void setBeaconColor(Location center, boolean isPlayerControlled) {
        World world = center.getWorld();

        // Place a 3x3 base of iron blocks below the beacon
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location baseLocation = new Location(world, center.getBlockX() + x, center.getBlockY() - 1, center.getBlockZ() + z);
                baseLocation.getBlock().setType(Material.IRON_BLOCK);
            }
        }

        // Place the beacon block
        Location beaconLocation = new Location(world, center.getBlockX(), center.getBlockY(), center.getBlockZ());
        beaconLocation.getBlock().setType(Material.BEACON);

        // Place stained glass above the beacon to set its color
        Location glassLocation = beaconLocation.clone().add(0, 1, 0);
        if (isPlayerControlled) {
            glassLocation.getBlock().setType(Material.BLUE_STAINED_GLASS); // Blue for player-controlled
        } else {
            glassLocation.getBlock().setType(Material.RED_STAINED_GLASS); // Red for mob-controlled
        }
    }

    //end sieges
    private void endSiege(List<Mob> mobs, boolean playersWon, boolean isDefensive) {
        if (bossSpawned) {
            removeBossMob();
        }
        removeTNTCannons(); // cleanup tnt cannons
        removeOffensiveKillBar(); // Ensure the offensive kill bar is cleared

        // Determine ownership based on siege outcome
        isPlayerControlled = playersWon;

        // Reset the stronghold
        resetStronghold();

        isOffensiveSiege = false;

        // Broadcast message
        if (playersWon) {
            Bukkit.broadcastMessage(isDefensive
                    ? "The stronghold was successfully defended!"
                    : "The stronghold has been successfully captured!");
            launchFireworks(strongholdCenter); // Fireworks celebration
        } else {
            Bukkit.broadcastMessage(isDefensive
                    ? "The stronghold has fallen! Get to safety!"
                    : "The siege has failed! Get to safety!");
        }

        // Schedule the next siege
        if (playersWon) {
            startSiegeAfterDelay();
        } else {
            startPlayerOffensiveSiege();
        }
    }



    private void removeBossMob() {
        if (bossMob != null && bossMob.isValid()) {
            bossMob.remove(); // Remove the boss mob from the world
        }
        bossMob = null; // Clear the reference
        bossSpawned = false; // Reset the spawn flag

        // Remove the boss bar if it exists
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    private void resetStronghold() {
        if (strongholdCenter != null) {
            // Reset terrain
            flattenTerrain(strongholdCenter);

            // Set beacon color based on ownership
            setBeaconColor(strongholdCenter, isPlayerControlled);

            // Recreate control rings
            createControlRings(strongholdCenter);

            // Recreate banners
            placeBanners(strongholdCenter);

            // Update banners to reflect current ownership
            updateBannerColors(isPlayerControlled);

            Bukkit.broadcastMessage("§eThe stronghold has been reset to its original state!");
        }
    }






    // Siege Mechanics Section
    private final List<Location> cannonLocations = new ArrayList<>(); // Store TNT cannon locations

    private void spawnTNTCannons(Location center) {
        World world = center.getWorld();
        int radius = RINGS[RINGS.length - 1]; // Outer ring radius
        double angleStep = Math.toRadians(360 / 4); // Divide circle into 4 parts

        // Place cannons equidistant around the stronghold
        for (int i = 0; i < 4; i++) {
            double angle = i * angleStep;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location cannonLoc = new Location(world, x, center.getY(), z);

            // Spawn the cannon (using Dispenser block as visual representation)
            cannonLoc.getBlock().setType(Material.DISPENSER);

            // Store the cannon location
            cannonLocations.add(cannonLoc);

            // Start firing TNT
            startCannonFiring(cannonLoc);
        }
    }

    private void startCannonFiring(Location cannonLoc) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Stop firing if the cannon no longer exists
                if (cannonLoc.getBlock().getType() != Material.DISPENSER) {
                    cancel();
                    return;
                }

                // Launch TNT from cannon location
                World world = cannonLoc.getWorld();
                Location tntSpawnLoc = cannonLoc.clone().add(0, 1, 0); // Spawn TNT above the dispenser
                TNTPrimed tnt = world.spawn(tntSpawnLoc, TNTPrimed.class);

                // Calculate direction toward the center ring
                Vector direction = strongholdCenter.clone()
                        .add(0, -2, 0) // Adjust target slightly downward for better landing
                        .toVector()
                        .subtract(tntSpawnLoc.toVector())
                        .normalize();

                // Scale the vector to control the distance and trajectory
                double speed = 1.5; // Adjust this value for further or shorter distances
                direction.multiply(speed);
                direction.setY(0.7); // Add an upward angle for a parabolic trajectory

                // Set the velocity and fuse time
                tnt.setVelocity(direction);
                tnt.setFuseTicks(80); // 4 seconds before explosion
            }
        }.runTaskTimer(this, 0L, 600L); // 30 seconds (600 ticks) between shots
    }



    private void removeTNTCannons() {
        // Remove all cannon blocks and clear the list
        for (Location cannonLoc : cannonLocations) {
            cannonLoc.getBlock().setType(Material.AIR); // Remove cannon
        }
        cannonLocations.clear();
    }




    // Passive - ensure no mobs spawn during siege that are not supposed to be there
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Cancel all hostile mob spawns unless they are custom spawns by your plugin
        if (event.getEntity() instanceof Mob && isHostileMob(event.getEntityType()) &&
                event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }
    // Utility method to check if a mob type is hostile
    private boolean isHostileMob(EntityType entityType) {
        return switch (entityType) {
            case ZOMBIE, SKELETON, CREEPER, SPIDER, ENDERMAN, ZOMBIE_VILLAGER, WITCH -> true;
            default -> false;
        };
    }
}


