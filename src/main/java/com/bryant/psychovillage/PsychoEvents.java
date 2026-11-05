package com.bryant.psychovillage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

@Mod.EventBusSubscriber(modid = PsychoVillageMod.MODID)
public class PsychoEvents {
    private static final String KILLER = "PsychoKiller";
    private static final String NAMED = "PsychoNamed";
    private static final String NAME = "PsychoName";
    private static final String[] NAMES = {
        "Alex", "Marta", "Daniel", "Lucia", "Mateo", "Sofia", "Carlos", "Elena",
        "Bruno", "Isabel", "Diego", "Valeria", "Samuel", "Clara", "Andres", "Nora",
        "Hector", "Paula", "Rafael", "Irene", "Javier", "Alicia", "Tomas", "Eva"
    };

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || event.getLevel().isClientSide()) return;
        if (!villager.getPersistentData().getBoolean(NAMED)) {
            String name = NAMES[villager.getRandom().nextInt(NAMES.length)];
            villager.getPersistentData().putString(NAME, name);
            villager.getPersistentData().putBoolean(NAMED, true);
            villager.setCustomName(Component.literal(name));
            villager.setCustomNameVisible(true);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % 100 != 0) return;
        long time = level.getDayTime() % 24000;
        if (time < 13000 || time > 23000) return;

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
        new AABB(
    level.getWorldBorder().getMinX(),
    level.getMinBuildHeight(),
    level.getWorldBorder().getMinZ(),
    level.getWorldBorder().getMaxX(),
    level.getMaxBuildHeight(),
    level.getWorldBorder().getMaxZ()
)
        if (villagers.size() < 2) return;

        Villager killer = villagers.stream()
            .filter(v -> v.getPersistentData().getBoolean(KILLER))
            .findFirst().orElse(null);

        if (killer == null) {
            killer = villagers.get(level.random.nextInt(villagers.size()));
            markKiller(killer);
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§cAlgo acecha en el pueblo..."), false);
        }

        Villager finalKiller = killer;
        List<Villager> victims = villagers.stream()
            .filter(v -> v != finalKiller && v.isAlive() && v.distanceToSqr(finalKiller) < 24 * 24)
            .toList();
        if (!victims.isEmpty() && level.random.nextInt(3) == 0) {
            Villager victim = victims.get(level.random.nextInt(victims.size()));
            openNearbyDoor(level, killer);
            level.playSound(null, victim.blockPosition(), SoundEvents.VILLAGER_HURT,
                SoundSource.HOSTILE, 1.0F, 0.65F);
            boolean hide = level.random.nextInt(4) == 0;
            makeBody(level, victim.blockPosition(), victim.getName().getString(), hide);
            victim.hurt(level.damageSources().mobAttack(killer), 1000.0F);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, victim.getX(), victim.getY() + 1,
                victim.getZ(), 8, 0.4, 0.4, 0.4, 0.02);
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§4¡Han encontrado a " + victim.getName().getString() + "!"), false);

            // A veces deja una pista con varios nombres del pueblo.
            if (level.random.nextInt(5) == 0) {
                dropNameClue(level, victim.blockPosition());
            }
        }

        // Si el asesino está cerca de un jugador durante la noche, puede atacarlo también.
        for (ServerPlayer player : players) {
            if (player.isAlive() && player.distanceToSqr(killer) < 9 * 9 && level.random.nextInt(8) == 0) {
                openNearbyDoor(level, killer);
                player.playSound(SoundEvents.VILLAGER_HURT, 1.0F, 0.55F);
                makeBody(level, player.blockPosition(), player.getName().getString(), false);
                player.hurt(level.damageSources().mobAttack(killer), 1000.0F);
                level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§4¡El asesino atacó a un jugador!"), false);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("psycho")
            .requires(source -> source.hasPermission(0))
            .then(Commands.literal("spawn").executes(ctx -> spawnKiller(ctx.getSource())))
            .then(Commands.literal("kill").executes(ctx -> killNearest(ctx.getSource())))
            .then(Commands.literal("attack").executes(ctx -> forceAttack(ctx.getSource(), false)))
            .then(Commands.literal("window").then(Commands.literal("attack")
                .executes(ctx -> forceAttack(ctx.getSource(), true)))));
    }

    private static int spawnKiller(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return 0;
        villager.moveTo(player.getX() + 2, player.getY(), player.getZ() + 2, player.getYRot(), 0);
        markKiller(villager);
        level.addFreshEntity(villager);
        source.sendSuccess(() -> Component.literal("§cEl asesino ha aparecido: " + villager.getName().getString()), true);
        return 1;
    }

    private static void markKiller(Villager villager) {
        villager.getPersistentData().putBoolean(KILLER, true);
        if (!villager.getPersistentData().getBoolean(NAMED)) {
            String name = NAMES[villager.getRandom().nextInt(NAMES.length)];
            villager.getPersistentData().putString(NAME, name);
            villager.getPersistentData().putBoolean(NAMED, true);
            villager.setCustomName(Component.literal(name));
            villager.setCustomNameVisible(true);
        }
        villager.setCustomName(Component.literal(villager.getPersistentData().getString(NAME) + " §4(¿?)"));
        villager.setCustomNameVisible(true);
    }

    private static int killNearest(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Villager target = player.level().getEntitiesOfClass(Villager.class,
            player.getBoundingBox().inflate(32)).stream().findFirst().orElse(null);
        if (target == null) {
            source.sendFailure(Component.literal("No hay aldeanos cerca."));
            return 0;
        }
        makeBody((ServerLevel) player.level(), target.blockPosition(), target.getName().getString());
        target.hurt(player.level().damageSources().playerAttack(player), 1000.0F);
        return 1;
    }

    private static int forceAttack(CommandSourceStack source, boolean window) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        Villager killer = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(64))
            .stream().filter(v -> v.getPersistentData().getBoolean(KILLER)).findFirst().orElse(null);
        if (killer == null) {
            source.sendFailure(Component.literal("No hay asesino cerca. Usa /psycho spawn."));
            return 0;
        }

        if (window) {
            BlockPos glass = findGlass(level, killer.blockPosition(), 6);
            if (glass != null) {
                level.destroyBlock(glass, false);
                killer.teleportTo(glass.getX() + 0.5, glass.getY(), glass.getZ() + 0.5);
                level.sendParticles(ParticleTypes.CLOUD, glass.getX() + 0.5, glass.getY() + 0.5,
                    glass.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
            }
        }

        LivingEntity target = level.getEntitiesOfClass(Villager.class, killer.getBoundingBox().inflate(20))
            .stream().filter(v -> v != killer).map(v -> (LivingEntity) v).findFirst().orElse(null);
        if (target == null) target = player;
        target.hurt(level.damageSources().mobAttack(killer), 1000.0F);
        if (target instanceof Villager victim) makeBody(level, victim.blockPosition(), victim.getName().getString());
        if (target instanceof ServerPlayer victimPlayer) makeBody(level, victimPlayer.blockPosition(), victimPlayer.getName().getString());
        source.sendSuccess(() -> Component.literal("§4¡El asesino ha atacado!"), true);
        return 1;
    }

    private static BlockPos findGlass(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.GLASS) || state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock
                || state.is(Blocks.GLASS_PANE) || state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static void makeBody(ServerLevel level, BlockPos pos, String name) {
        makeBody(level, pos, name, false);
    }

    private static void makeBody(ServerLevel level, BlockPos pos, String name, boolean hideUnderBed) {
        ArmorStand body = EntityType.ARMOR_STAND.create(level);
        if (body == null) return;
        BlockPos bodyPos = pos;
        if (hideUnderBed) {
            BlockPos bed = findNearbyBed(level, pos, 5);
            if (bed != null) bodyPos = bed;
        }
        body.moveTo(bodyPos.getX() + 0.5, bodyPos.getY(), bodyPos.getZ() + 0.5,
            level.random.nextFloat() * 360, 0);
        body.setNoGravity(true);
        body.setShowArms(true);
        body.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.PLAYER_HEAD));
        if (hideUnderBed) {
            body.setInvisible(true);
            body.setCustomName(Component.literal("§4Evidencia oculta"));
            body.setCustomNameVisible(false);
        } else {
            body.setCustomName(Component.literal("§4Cuerpo de " + name));
            body.setCustomNameVisible(true);
        }
        level.addFreshEntity(body);
        if (!hideUnderBed) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, bodyPos.getX() + 0.5, bodyPos.getY() + 0.1,
                bodyPos.getZ() + 0.5, 12, 0.5, 0.05, 0.5, 0.0);
            level.sendParticles(ParticleTypes.CRIT, bodyPos.getX() + 0.5, bodyPos.getY() + 0.1,
                bodyPos.getZ() + 0.5, 8, 0.35, 0.05, 0.35, 0.0);
        }
    }

    private static BlockPos findNearbyBed(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius),
                center.offset(radius, 2, radius))) {
            if (level.getBlockState(pos).getBlock() instanceof BedBlock) return pos.immutable();
        }
        return null;
    }

    private static void openNearbyDoor(ServerLevel level, Villager killer) {
        BlockPos center = killer.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 2, 2))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && state.hasProperty(BlockStateProperties.OPEN)
                && !state.getValue(BlockStateProperties.OPEN)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 3);
                level.playSound(null, pos, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
                return;
            }
        }
    }

    private static void dropNameClue(ServerLevel level, BlockPos pos) {
        net.minecraft.world.entity.item.ItemEntity clue = new net.minecraft.world.entity.item.ItemEntity(
            level, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, new ItemStack(Items.PAPER));
        clue.setPickUpDelay(20);
        clue.setCustomName(Component.literal("§eLista de sospechosos"));
        clue.setCustomNameVisible(true);
        level.addFreshEntity(clue);
    }
}
