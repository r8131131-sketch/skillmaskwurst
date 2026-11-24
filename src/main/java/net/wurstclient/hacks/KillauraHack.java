package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.Random;
import java.util.stream.StreamSupport;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"kill aura", "ka", "pvp"})
public final class KillauraHack extends Hack implements UpdateListener {
    private enum ServerMode {
        RW("RW"),
        FT("FT"),
        SPOOKYTIME("SpookyTime"),
        COPY_RW("Копия РВ");

        private final String name;
        ServerMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final EnumSetting<ServerMode> server = new EnumSetting<>("Сервер",
        "Настройка дистанции, ротации и критов под античит сервера.", ServerMode.values(), ServerMode.RW);

    private final CheckboxSetting onlyCrits = new CheckboxSetting("Только криты",
        "Бить в прыжке при падении.", true);

    private final CheckboxSetting ignoreFriends = new CheckboxSetting("Не бить тиммейтов",
        "Игнорировать игроков из списка друзей (.friends add Ник).", true);

    private final CheckboxSetting onlyPlayers = new CheckboxSetting("Только игроки",
        "Не бить мобов и животных, только игроков.", true);

    private final Random random = new Random();
    private Entity lockedTarget = null;
    private boolean vKeyPressed = false;

    public KillauraHack() {
        super("Killaura");
        setCategory(Category.COMBAT);
        addSetting(server);
        addSetting(onlyCrits);
        addSetting(ignoreFriends);
        addSetting(onlyPlayers);
    }

    @Override
    protected void onEnable() {
        lockedTarget = null;
        EVENTS.add(UpdateListener.class, this);
    }

    @Override
    protected void onDisable() {
        lockedTarget = null;
        EVENTS.remove(UpdateListener.class, this);
    }

    @Override
    public void onUpdate() {
        if (MC.player == null || MC.world == null) return;

        // Обработка нажатия клавиши V для захвата/сброса цели
        handleTargetLockKey();

        ServerMode mode = server.getSelected();
        double maxRange = switch (mode) {
            case RW -> 3.05;
            case FT -> 3.15;
            case SPOOKYTIME -> 3.6;
            case COPY_RW -> 4.5;
        };

        Entity target = null;

        // Если захвачена конкретная цель через V
        if (lockedTarget != null) {
            if (!lockedTarget.isAlive() || lockedTarget.isRemoved()) {
                sendChat("§c[Killaura] Таргет сброшен (цель недоступна).");
                lockedTarget = null;
            } else if (MC.player.distanceTo(lockedTarget) <= maxRange) {
                target = lockedTarget;
            } else {
                // Цель убежала дальше радиуса удара — ждём, пока подойдёт ближе, других не бьём
                return;
            }
        } else {
            // Обычный поиск ближайшего врага с учётом тиммейтов и настроек
            target = StreamSupport.stream(MC.world.getEntities().spliterator(), false)
                .filter(this::isValidTarget)
                .filter(e -> MC.player.distanceTo(e) <= maxRange)
                .min(Comparator.comparingDouble(e -> MC.player.distanceTo(e)))
                .orElse(null);
        }

        if (target == null) return;

        aimAt(target, mode);

        float cooldownThreshold = (mode == ServerMode.RW || mode == ServerMode.FT)
            ? 0.92F + random.nextFloat() * 0.06F
            : 0.90F;

        if (MC.player.getAttackCooldownProgress(0.5F) < cooldownThreshold) return;

        boolean canCrit = !onlyCrits.isChecked() || MC.player.isOnGround() || MC.player.fallDistance > 0.12F;
        if (!canCrit) return;

        MC.interactionManager.attackEntity(MC.player, target);
        MC.player.swingHand(Hand.MAIN_HAND);
    }

    private void handleTargetLockKey() {
        if (MC.currentScreen != null) return;

        boolean isDown = InputUtil.isKeyPressed(MC.getWindow(), GLFW.GLFW_KEY_V);
        if (isDown && !vKeyPressed) {
            vKeyPressed = true;
            if (lockedTarget != null) {
                sendChat("§e[Killaura] Таргет сброшен: " + lockedTarget.getName().getString());
                lockedTarget = null;
            } else if (MC.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity entity) {
                if (ignoreFriends.isChecked() && entity instanceof PlayerEntity p && WURST.getFriends().contains(p.getName().getString())) {
                    sendChat("§c[Killaura] Нельзя взять тиммейта в таргет!");
                    return;
                }
                lockedTarget = entity;
                sendChat("§a[Killaura] Таргет захвачен: §f" + entity.getName().getString());
            } else {
                sendChat("§7[Killaura] Наведи прицел на игрока и нажми V.");
            }
        } else if (!isDown) {
            vKeyPressed = false;
        }
    }

    private boolean isValidTarget(Entity e) {
        if (e == MC.player || !(e instanceof LivingEntity l) || !l.isAlive()) return false;
        if (onlyPlayers.isChecked() && !(e instanceof PlayerEntity)) return false;

        // Проверка на тиммейта (список друзей Wurst)
        if (ignoreFriends.isChecked() && e instanceof PlayerEntity p) {
            if (WURST.getFriends().contains(p.getName().getString())) {
                return false;
            }
        }
        return true;
    }

    private void sendChat(String msg) {
        if (MC.inGameHud != null) {
            MC.inGameHud.getChatHud().addMessage(Text.literal(msg));
        }
    }

    private void aimAt(Entity target, ServerMode mode) {
        Vec3d eyePos = MC.player.getEyePos();
        Vec3d targetPos = target.getBoundingBox().getCenter();
        double diffX = targetPos.x - eyePos.x;
        double diffY = targetPos.y - eyePos.y;
        double diffZ = targetPos.z - eyePos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        switch (mode) {
            case RW:
            case FT:
                float jitterYaw = (random.nextFloat() - 0.5F) * 1.8F;
                float jitterPitch = (random.nextFloat() - 0.5F) * 1.2F;
                MC.player.setYaw(MathHelper.lerpAngleDegrees(0.32F, MC.player.getYaw(), targetYaw + jitterYaw));
                MC.player.setPitch(MathHelper.lerp(0.32F, MC.player.getPitch(), targetPitch + jitterPitch));
                break;

            case SPOOKYTIME:
                MC.player.setYaw(MathHelper.lerpAngleDegrees(0.55F, MC.player.getYaw(), targetYaw));
                MC.player.setPitch(MathHelper.lerp(0.55F, MC.player.getPitch(), targetPitch));
                break;

            case COPY_RW:
                MC.player.setYaw(targetYaw);
                MC.player.setPitch(targetPitch);
                break;
        }
    }
}
