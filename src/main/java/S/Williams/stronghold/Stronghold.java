package S.Williams.stronghold;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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

public class Stronghold extends JavaPlugin implements Listener, CommandExecutor {

    private Location strongholdCenter;
    private boolean isPlayerControlled = true; // Indicates stronghold ownership
    private final int RADIUS = 80; // Radius for the flat area
    private final int[] RINGS = {25, 40, 60}; // Radii for each ring

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

            for (int degree = 0; degree < 360; degree += 5) { // Adjust 5 for denser block placement
                double radians = Math.toRadians(degree);
                double x = center.getX() + radius * Math.cos(radians);
                double z = center.getZ() + radius * Math.sin(radians);
                Location blockLoc = new Location(world, x, center.getY(), z);

                // Set the block at this location to the ring material, flush with the ground
                blockLoc.getBlock().setType(material);
            }
        }
    }

    private void checkPlayerCaptureCondition(List<Mob> defendingMobs) {
        int captureRadius = RINGS[0]; // Inner ring defines the capture radius

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(strongholdCenter.getWorld())
                    && player.getLocation().distance(strongholdCenter) <= captureRadius) {

                // Player has reached the center, capture the stronghold
                isPlayerControlled = true; // Mark the stronghold as player-controlled
                Bukkit.broadcastMessage("The players have captured the stronghold!");

                // Update the beacon to blue
                setBeaconColor(strongholdCenter, true);

                // Cancel any running offensive siege tasks
                if (siegeTimer != null) {
                    siegeTimer.cancel();
                }

                // End the offensive siege
                endSiege(defendingMobs);
                return;
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
                if (x * x + z * z <= RADIUS * RADIUS) {
                    Location blockLoc = new Location(world, centerX + x, centerY, centerZ + z);
                    blockLoc.getBlock().setType(Material.GRASS_BLOCK);

                    // Clear blocks above to make sure it's a flat surface
                    for (int y = centerY + 1; y <= centerY + 5; y++) {
                        Location airLoc = new Location(world, centerX + x, y, centerZ + z);
                        airLoc.getBlock().setType(Material.AIR);
                    }
                }
            }
        }

        // Display control rings with colored blocks
        createControlRings(center);
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

    private void startSiegeAfterDelay() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage("A mob siege has begun! Defend the stronghold!");

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




    private List<Mob> spawnSiegeMobs() {
        if (!isPlayerControlled) return null; // Ensure stronghold is still player-controlled

        World world = strongholdCenter.getWorld();
        int spawnRadius = RINGS[RINGS.length - 1]; // Spawn mobs at the outermost ring
        List<Mob> mobs = new ArrayList<>();
        int maxMobs = 50; // Maintain a constant mob count

        // Initial spawning of mobs
        for (int i = 0; i < maxMobs; i++) {
            spawnMobAtRandomLocation(mobs, spawnRadius, world);
        }

        // Periodically check for movement toward the center
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isPlayerControlled) {
                    cancel();
                    return;
                }

                // Ensure mobs are guided toward the center
                guideMobsToCenter(mobs);

                // Replace dead mobs to maintain the constant count
                while (mobs.size() < maxMobs) {
                    spawnMobAtRandomLocation(mobs, spawnRadius, world);
                }
            }
        }.runTaskTimer(this, 0, 20L); // Check and guide every second (20 ticks)

        return mobs; // Return the list of spawned mobs
    }


    private void spawnMobAtRandomLocation(List<Mob> mobs, int spawnRadius, World world) {
        double angle = Math.toRadians(new Random().nextInt(360));
        double x = strongholdCenter.getX() + spawnRadius * Math.cos(angle);
        double z = strongholdCenter.getZ() + spawnRadius * Math.sin(angle);
        Location spawnLocation = new Location(world, x, strongholdCenter.getY() + 4, z);

        Mob mob = (Mob) world.spawnEntity(spawnLocation, EntityType.ZOMBIE); // Change to preferred mob type
        mob.setTarget(null); // Start without a target
        mobs.add(mob);
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



    private BukkitRunnable siegeTimer;

    private void startSiegeTimer(List<Mob> mobs) {
        // Cancel the previous timer if it exists
        if (siegeTimer != null) {
            siegeTimer.cancel();
        }

        siegeTimer = new BukkitRunnable() {
            int timeLeft = 60; // 60 seconds for the siege

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    // Timer ends without mob capture, stronghold defended
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendActionBar("§aStronghold successfully defended!");
                    }
                    endSiege(mobs); // End the siege cleanly
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




    private void checkCaptureCondition(List<Mob> mobs) {
        int captureRadius = RINGS[0]; // Inner ring defines the capture radius

        for (Mob mob : mobs) {
            if (mob != null && mob.isValid() && mob.getLocation().distance(strongholdCenter) <= captureRadius) {
                // Mob has reached the center, capture the stronghold
                isPlayerControlled = false; // Mark the stronghold as mob-controlled
                Bukkit.broadcastMessage("The stronghold has been captured by mobs!");
                setBeaconColor(strongholdCenter, false); // Set beacon to red

                // Cancel the timer and end the siege
                if (siegeTimer != null) {
                    siegeTimer.cancel();
                }

                endSiege(mobs);

                // Start a player offensive siege after 1 minute
                startPlayerOffensiveSiege();
                return;
            }
        }
    }

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




    private void endSiege(List<Mob> mobs) {
        // Remove all siege mobs
        for (Mob mob : mobs) {
            if (mob != null && mob.isValid()) {
                mob.remove();
            }
        }

        // Update beacon color based on ownership
        setBeaconColor(strongholdCenter, isPlayerControlled);

        if (isPlayerControlled) {
            Bukkit.broadcastMessage("The siege is over. The stronghold remains player-controlled!");
        } else {
            Bukkit.broadcastMessage("The siege is over. The stronghold is now mob-controlled!");
        }

        // Schedule the next siege
        scheduleNextSiege();
    }


    private void startPlayerOffensiveSiege() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage("A player offensive siege has begun! Capture the stronghold!");

                // Spawn defending mobs inside the stronghold
                List<Mob> defendingMobs = spawnDefendingMobs();

                // Start timer for the player offensive siege
                startPlayerSiegeTimer(defendingMobs);
            }
        }.runTaskLater(this, 1200L); // Delay of 1 minute (1200 ticks)
    }

    private List<Mob> spawnDefendingMobs() {
        World world = strongholdCenter.getWorld();
        int spawnRadius = RINGS[0]; // Spawn mobs inside the innermost ring
        List<Mob> mobs = new ArrayList<>();
        int maxMobs = 50; // Number of defending mobs

        for (int i = 0; i < maxMobs; i++) {
            spawnMobAtRandomLocation(mobs, spawnRadius, world);
        }

        return mobs;
    }

    private void startPlayerSiegeTimer(List<Mob> defendingMobs) {
        siegeTimer = new BukkitRunnable() {
            int timeLeft = 60; // 60 seconds for the offensive siege

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    // Timer ends without player capture, stronghold remains mob-controlled
                    Bukkit.broadcastMessage("The offensive siege has failed! The stronghold remains mob-controlled.");
                    endSiege(defendingMobs);
                    cancel();
                    return;
                }

                // Check if a player has captured the stronghold
                checkPlayerCaptureCondition(defendingMobs);

                // Display countdown timer in Action Bar for all players
                String timeFormatted = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60); // Format as MM:SS
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar("§eOffensive Siege Time Remaining: §c" + timeFormatted);
                }

                timeLeft--;
            }
        };

        siegeTimer.runTaskTimer(this, 0, 20L); // Runs every second (20 ticks)
    }

    private void scheduleNextSiege() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isPlayerControlled) {
                    // Start a mob siege after 1 minute
                    Bukkit.broadcastMessage("The mobs are preparing to attack the stronghold!");
                    startSiegeAfterDelay();
                } else {
                    // Start a player offensive siege after 1 minute
                    Bukkit.broadcastMessage("Prepare for a player offensive siege to retake the stronghold!");
                    startPlayerOffensiveSiege();
                }
            }
        }.runTaskLater(this, 1200L); // 1 minute (1200 ticks) delay
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

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Cancel any hostile mob spawns unless they are part of the siege
        if (event.getEntityType() == EntityType.ZOMBIE || event.getEntityType() == EntityType.SKELETON || event.getEntityType() == EntityType.CREEPER || event.getEntityType() == EntityType.SPIDER || event.getEntityType() == EntityType.ENDERMAN || event.getEntityType() == EntityType.ZOMBIE_VILLAGER || event.getEntityType() == EntityType.WITCH) {
            if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
                event.setCancelled(true);
            }
        }
    }
}



