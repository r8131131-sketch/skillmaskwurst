package net.wurstclient.hacks;

import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"speed", "bhop", "strafe"})
public final class SpeedHack extends Hack implements UpdateListener {
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
        "Выбор обхода античита под конкретный сервер.", ServerMode.values(), ServerMode.RW);

    public SpeedHack() {
        super("Speed");
        setCategory(Category.MOVEMENT);
        addSetting(server);
    }

    @Override
    protected void onEnable() {
        EVENTS.add(UpdateListener.class, this);
    }

    @Override
    protected void onDisable() {
        EVENTS.remove(UpdateListener.class, this);
    }

    @Override
    public void onUpdate() {
        if (MC.player == null || MC.player.isSneaking()) return;
        if (MC.player.forwardSpeed == 0 && MC.player.sidewaysSpeed == 0) return;

        MC.player.setSprinting(true);
        ServerMode mode = server.getSelected();
        Vec3d vel = MC.player.getVelocity();

        switch (mode) {
            case RW:
            case FT:
                // Grim/Vulcan обход: обычный прыжок + легкий буст только при отталкивании (1.06x)
                if (MC.player.isOnGround()) {
                    MC.player.jump();
                    MC.player.setVelocity(vel.x * 1.06, MC.player.getVelocity().y, vel.z * 1.06);
                }
                applyStrafe(0.98);
                break;

            case SPOOKYTIME:
                // LowHop: низкий быстрый прыжок с ускорением
                if (MC.player.isOnGround()) {
                    MC.player.setVelocity(vel.x * 1.18, 0.34, vel.z * 1.18);
                }
                applyStrafe(1.0);
                break;

            case COPY_RW:
                // Агрессивный BunnyHop под слабые античиты
                if (MC.player.isOnGround()) {
                    MC.player.jump();
                    MC.player.setVelocity(vel.x * 1.38, MC.player.getVelocity().y, vel.z * 1.38);
                }
                applyStrafe(1.02);
                break;
        }
    }

    private void applyStrafe(double friction) {
        Vec3d vel = MC.player.getVelocity();
        double currentSpeed = Math.hypot(vel.x, vel.z) * friction;
        float yaw = (float) Math.toRadians(MC.player.getYaw());
        double forward = MC.player.forwardSpeed;
        double strafe = MC.player.sidewaysSpeed;

        if (forward != 0 || strafe != 0) {
            if (forward != 0) {
                if (strafe > 0) yaw += (forward > 0 ? -0.785f : 0.785f);
                else if (strafe < 0) yaw += (forward > 0 ? 0.785f : -0.785f);
                strafe = 0;
                forward = forward > 0 ? 1 : -1;
            }
            double newX = -Math.sin(yaw) * forward * currentSpeed + Math.cos(yaw) * strafe * currentSpeed;
            double newZ = Math.cos(yaw) * forward * currentSpeed + Math.sin(yaw) * strafe * currentSpeed;
            MC.player.setVelocity(newX, vel.y, newZ);
        }
    }
}
