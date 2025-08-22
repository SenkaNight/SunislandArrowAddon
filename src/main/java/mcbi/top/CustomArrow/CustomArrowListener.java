package mcbi.top.CustomArrow;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.lib.api.event.AttackEvent;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.damage.ProjectileAttackMetadata;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Arrow arrow = (Arrow) event.getProjectile();
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
        arrow.setMetadata("CustomArrowID", new FixedMetadataValue(plugin, arrowId));
        arrow.setMetadata("CustomArrowDamageMultiplier",
                new FixedMetadataValue(plugin, customArrow.getExtraDamage()));
        arrow.setMetadata("CustomArrowParticle",
                new FixedMetadataValue(plugin, customArrow.getParticle().name()));
        arrow.setMetadata("CustomArrowParticleCount",
                new FixedMetadataValue(plugin, customArrow.getParticleCount()));
        arrow.setMetadata("CustomArrowShooter",
                new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        new BukkitRunnable() {
            public void run() {
                if (arrow.isDead() || arrow.isOnGround()) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(
                        customArrow.getParticle(),
                        arrow.getLocation(),
                        customArrow.getParticleCount(),
                        0.05, 0.05, 0.5,
                        0.01
                );
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (!(event.getAttack() instanceof ProjectileAttackMetadata)) {
            return;
        }
        ProjectileAttackMetadata projectileAttack = (ProjectileAttackMetadata) event.getAttack();
        if (!(projectileAttack.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        if (!arrow.hasMetadata("CustomArrowDamageMultiplier")) {
            return;
        }
        double damageMultiplier = arrow.getMetadata("CustomArrowDamageMultiplier").get(0).asDouble();
        event.getDamage().multiplicativeModifier(damageMultiplier, DamageType.PHYSICAL);
//        plugin.getLogger().info("应用自定义箭矢伤害倍率: " + damageMultiplier);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!arrow.hasMetadata("CustomArrowID") || !arrow.hasMetadata("CustomArrowShooter")) {
            return;
        }
        String arrowId = arrow.getMetadata("CustomArrowID").get(0).asString();
        CustomArrow customArrow = arrowManager.getArrow(arrowId);
        if (customArrow == null) {
            return;
        }
        String shooterUUID = arrow.getMetadata("CustomArrowShooter").get(0).asString();
        Player shooter = plugin.getServer().getPlayer(java.util.UUID.fromString(shooterUUID));
        if (shooter == null || !shooter.isOnline()) {
            return;
        }
        if (arrow.hasMetadata("CustomArrowParticle")) {
            Particle particle = Particle.valueOf(
                    arrow.getMetadata("CustomArrowParticle").get(0).asString());
            int count = arrow.getMetadata("CustomArrowParticleCount").get(0).asInt();

            arrow.getWorld().spawnParticle(
                    particle,
                    arrow.getLocation(),
                    count,
                    0.2, 0.2, 0.2,
                    0.05
            );
        }

        if (!customArrow.getMythicSkills().isEmpty()) {
            Entity target = event.getHitEntity();
            for (String skill : customArrow.getMythicSkills()) {
                try {
                    mythicAPI.castSkill(shooter, skill, arrow.getLocation());
//                    plugin.getLogger().info("触发MythicMobs技能: " + skill);
                } catch (Exception e) {
//                    plugin.getLogger().warning("触发MythicMobs技能时出错: " + skill);
                    e.printStackTrace();
                }
            }
        }
    }
}