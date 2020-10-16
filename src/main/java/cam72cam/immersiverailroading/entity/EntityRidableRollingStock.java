package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.custom.IRidable;
import cam72cam.mod.input.Keyboard;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class EntityRidableRollingStock extends EntityBuildableRollingStock implements IRidable {
	public float getRidingSoundModifier() {
		return getDefinition().dampeningAmount;
	}

	@Override
	public ClickResult onClick(Player player, Player.Hand hand) {
		ClickResult clickRes = super.onClick(player, hand);
		if (clickRes != ClickResult.PASS) {
			return clickRes;
		}

		if (player.isCrouching()) {
			return ClickResult.PASS;
		} else if (isPassenger(player)) {
			return ClickResult.PASS;
		} else {
			if (getWorld().isServer) {
				player.startRiding(this);
			}
			return ClickResult.ACCEPTED;
		}
	}

	@Override
	public Vec3d getMountOffset(Entity passenger, Vec3d off) {
		int wiggle = passenger.isVillager() ? 10 : 2;
		off = off.add((Math.random()-0.5) * wiggle, 0, (Math.random()-0.5) * wiggle);
		off = this.getDefinition().correctPassengerBounds(gauge, off, shouldRiderSit(passenger));
		return off;
	}

	@Override
	public boolean canFitPassenger(Entity passenger) {
		return getPassengerCount() < this.getDefinition().getMaxPassengers();
	}
	
	@Override
	public boolean shouldRiderSit(Entity passenger) {
		if (this.getDefinition().shouldSit != null) {
			return this.getDefinition().shouldSit;
		}
		return this.gauge.shouldSit();
	}

	@Override
	public Vec3d onPassengerUpdate(Entity passenger, Vec3d offset) {
		if (passenger.isPlayer()) {
			offset = playerMovement(passenger.asPlayer(), offset);
		}

		offset = this.getDefinition().correctPassengerBounds(gauge, offset, shouldRiderSit(passenger));

		return offset;
	}

	private static final Map<UUID, Integer> cooldown = new HashMap<>();
	private Vec3d playerMovement(Player source, Vec3d offset) {
		Vec3d movement = source.getMovementInput();
        /*
        if (sprinting) {
            movement = movement.scale(3);
        }
        */
        if (movement.length() < 0.1) {
            return offset;
        }

        movement = new Vec3d(movement.x, 0, movement.z).rotateMinecraftYaw(source.getRotationYawHead()-this.getRotationYaw());

        offset = offset.add(movement);

        if (this instanceof EntityCoupleableRollingStock && cooldown.getOrDefault(source.getUUID(), 0) < source.getTickCount()) {
			EntityCoupleableRollingStock couplable = (EntityCoupleableRollingStock) this;

			boolean atFront = this.getDefinition().isAtFront(gauge, offset);
			boolean atBack = this.getDefinition().isAtRear(gauge, offset);

			for (CouplerType coupler : CouplerType.values()) {
				boolean atCoupler = coupler == CouplerType.FRONT ? atFront : atBack;
				if (atCoupler && couplable.isCoupled(coupler)) {
					EntityCoupleableRollingStock coupled = ((EntityCoupleableRollingStock) this).getCoupled(coupler);
					if (coupled != null) {
						cooldown.put(source.getUUID(), source.getTickCount() + 10);
						coupled.addPassenger(source);
					} else if (this.getTickCount() > 20) {
						ImmersiveRailroading.info(
								"Tried to move between cars (%s, %s), but %s was not found",
								this.getUUID(),
								couplable.getCoupledUUID(coupler),
								couplable.getCoupledUUID(coupler)
						);
						((EntityCoupleableRollingStock) this).decouple(coupler);
					}
					return offset;
				}
			}
        }
		return offset;
	}

	public Vec3d onDismountPassenger(Entity passenger, Vec3d offset) {
		//TODO calculate better dismount offset
		offset = new Vec3d(Math.copySign(getDefinition().getWidth(gauge)/2 + 1, offset.x), 0, offset.z);

		if (passenger.isVillager()) {
			double distanceMoved = passenger.getPosition().distanceTo(getPosition());

			int payout = (int) Math.floor(distanceMoved * Config.ConfigBalance.villagerPayoutPerMeter);

			List<ItemStack> payouts = Config.ConfigBalance.getVillagerPayout();
			if (payouts.size() != 0) {
				int type = (int)(Math.random() * 100) % payouts.size();
				ItemStack stack = payouts.get(type).copy();
				stack.setCount(payout);
				getWorld().dropItem(stack, getBlockPosition());
				// TODO drop by player or new pos?
			}
		}

		return offset;
	}
}
