package dev.goldenhead.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Left/right clicks on blocks and entities, the way the server sees a real click. */
public final class Clicker {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private Clicker() {
    }

    public static void block(BlockPos pos, boolean left, boolean right, boolean swing) {
        Vec3d aim = Vec3d.ofCenter(pos);
        Direction side = visibleSide(pos, aim);
        // Raw start/abort so the left click reaches the server even in adventure mode, where
        // the client would otherwise not send a block hit at all.
        if (left) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, side));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, side));
        }
        if (right) mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(aim, side, pos, false));
        if (swing) mc.player.swingHand(Hand.MAIN_HAND);
    }

    public static void entity(Entity entity, boolean left, boolean right, boolean swing) {
        if (left) mc.interactionManager.attackEntity(mc.player, entity);
        if (right) mc.interactionManager.interactEntity(mc.player, entity, Hand.MAIN_HAND);
        if (swing) mc.player.swingHand(Hand.MAIN_HAND);
    }

    // The face we are actually looking at, or the one facing us if something is in the way.
    private static Direction visibleSide(BlockPos pos, Vec3d aim) {
        Vec3d eye = mc.player.getEyePos();
        BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, aim, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return hit.getSide();
        return Direction.getFacing(eye.subtract(aim));
    }
}
