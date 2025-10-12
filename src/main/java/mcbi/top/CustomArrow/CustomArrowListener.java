package mcbi.top.CustomArrow;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.lib.api.event.AttackEvent;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.damage.ProjectileAttackMetadata;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class CustomArrowListener implements Listener {
    private final CustomArrowManager arrowManager;
    private final JavaPlugin plugin;
    private final BukkitAPIHelper mythicAPI;

    public CustomArrowListener(JavaPlugin plugin, CustomArrowManager arrowManager) {
        this.plugin = plugin;
        this.arrowManager = arrowManager;
        this.mythicAPI = MythicBukkit.inst().getAPIHelper();
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Entity projectile = event.getProjectile();
        if (!isSupportedProjectile(projectile)) {
            return;
        }
        Player player = (Player) event.getEntity();
        ItemStack arrowItem = event.getConsumable();
        if (arrowItem == null || !arrowManager.isCustomArrow(arrowItem)) {
            return;
        }
        String arrowId = arrowManager.getArrowId(arrowItem);
        CustomArrow customArrow = arrowManager.getArrow(arrowId);
        if (customArrow == null) {
            plugin.getLogger().warning("Cannot find arrow config: " + arrowId);
            return;
        }

        projectile.setMetadata("CustomArrowID", new FixedMetadataValue(plugin, arrowId));
        projectile.setMetadata("CustomArrowDamageMultiplier",
                new FixedMetadataValue(plugin, customArrow.getExtraDamage()));
        projectile.setMetadata("CustomArrowShooter",
                new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        if (customArrow.getParticle() != null) {
            startParticleTask(projectile, customArrow);
        }
    }
    private void startParticleTask(Entity projectile, CustomArrow customArrow) {
        new BukkitRunnable() {
            public void run() {
                if (projectile == null || projectile.isDead() || !projectile.isValid()) {
                    cancel();
                    return;
                }
                if (projectile instanceof AbstractArrow) {
                    AbstractArrow arrow = (AbstractArrow) projectile;
                    if (arrow.isInBlock() || arrow.isOnGround()) {
                        cancel();
                        return;
                    }
                } else if (projectile instanceof ThrownPotion) {
                    ThrownPotion potion = (ThrownPotion) projectile;
                    if (potion.isOnGround()) {
                        cancel();
                        return;
                    }
                } else if (projectile instanceof Firework) {
                    Firework firework = (Firework) projectile;
                    if (firework.isDetonated()) {
                        cancel();
                        return;
                    }
                }
                try {
                    projectile.getWorld().spawnParticle(
                            customArrow.getParticle(),
                            projectile.getLocation(),
                            customArrow.getParticleCount(),
                            0.05, 0.05, 0.1,
                            0.02
                    );
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to spawn particle: " + e.getMessage());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (!(event.getAttack() instanceof ProjectileAttackMetadata)) {
            return;
        }
        ProjectileAttackMetadata projectileAttack = (ProjectileAttackMetadata) event.getAttack();
        Entity projectile = projectileAttack.getProjectile();
        if (projectile != null && projectile.hasMetadata("CustomArrowDamageMultiplier")) {
            double damageMultiplier = projectile.getMetadata("CustomArrowDamageMultiplier").get(0).asDouble();
            event.getDamage().multiplicativeModifier(damageMultiplier, DamageType.PROJECTILE);
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        Entity projectile = event.getEntity();
        if (!isSupportedProjectile(projectile) ||
                !projectile.hasMetadata("CustomArrowID") ||
                !projectile.hasMetadata("CustomArrowShooter")) {
            return;
        }
        String arrowId = projectile.getMetadata("CustomArrowID").get(0).asString();
        CustomArrow customArrow = arrowManager.getArrow(arrowId);
        if (customArrow == null) {
            return;
        }

        String shooterUUID = projectile.getMetadata("CustomArrowShooter").get(0).asString();
        Player shooter = plugin.getServer().getPlayer(java.util.UUID.fromString(shooterUUID));
        if (shooter == null || !shooter.isOnline()) {
            return;
        }
        if (customArrow.getParticle() != null) {
            spawnHitParticles(projectile, customArrow);
        }
        if (!customArrow.getMythicSkills().isEmpty()) {
            for (String skill : customArrow.getMythicSkills()) {
                try {
                    mythicAPI.castSkill(shooter, skill, projectile.getLocation());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to cast skill: " + skill + " - " + e.getMessage());
                }
            }
        }
    }
    private void spawnHitParticles(Entity projectile, CustomArrow customArrow) {
        Particle particle = customArrow.getParticle();
        int count = customArrow.getParticleCount();

        try {
            if (projectile instanceof Firework) {
                projectile.getWorld().spawnParticle(
                        particle,
                        projectile.getLocation(),
                        count * 10,
                        1.0, 1.0, 1.0,
                        0.3
                );
            } else {
                projectile.getWorld().spawnParticle(
                        particle,
                        projectile.getLocation(),
                        count * 5,
                        0.5, 0.5, 0.5,
                        0.1
                );
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn hit particle: " + e.getMessage());
        }
    }

    private boolean isSupportedProjectile(Entity entity) {
        return entity instanceof AbstractArrow ||
                entity instanceof ThrownPotion ||
                entity instanceof Firework;
    }
}