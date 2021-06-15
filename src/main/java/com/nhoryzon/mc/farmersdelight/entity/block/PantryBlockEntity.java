package com.nhoryzon.mc.farmersdelight.entity.block;

import com.nhoryzon.mc.farmersdelight.FarmersDelightMod;
import com.nhoryzon.mc.farmersdelight.block.PantryBlock;
import com.nhoryzon.mc.farmersdelight.registry.BlockEntityTypesRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestStateManager;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.util.Objects;

public class PantryBlockEntity extends LootableContainerBlockEntity {
    private static final int MAX_INVENTORY_SIZE = 27;

    private final ChestStateManager stateManager;
    private DefaultedList<ItemStack> content;

    public PantryBlockEntity(BlockPos blockPos, BlockState blockState) {
        this(BlockEntityTypesRegistry.PANTRY.get(), blockPos, blockState);
    }

    private PantryBlockEntity(BlockEntityType<?> type, BlockPos blockPos, BlockState blockState) {
        super(type, blockPos, blockState);
        this.content = DefaultedList.ofSize(MAX_INVENTORY_SIZE, ItemStack.EMPTY);
        this.stateManager = new ChestStateManager() {
            protected void onChestOpened(World world, BlockPos pos, BlockState state) {
                PantryBlockEntity.this.playSound(state, SoundEvents.BLOCK_BARREL_OPEN);
                PantryBlockEntity.this.setOpen(state, true);
            }

            protected void onChestClosed(World world, BlockPos pos, BlockState state) {
                PantryBlockEntity.this.playSound(state, SoundEvents.BLOCK_BARREL_CLOSE);
                PantryBlockEntity.this.setOpen(state, false);
            }

            protected void onInteracted(World world, BlockPos pos, BlockState state, int oldViewerCount, int newViewerCount) {
            }

            protected boolean isPlayerViewing(PlayerEntity player) {
                if (player.currentScreenHandler instanceof GenericContainerScreenHandler) {
                    Inventory inventory = ((GenericContainerScreenHandler)player.currentScreenHandler).getInventory();
                    return inventory == PantryBlockEntity.this;
                } else {
                    return false;
                }
            }
        };
    }

    @Override
    protected Text getContainerName() {
        return FarmersDelightMod.i18n("container.pantry");
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
    }

    @Override
    protected DefaultedList<ItemStack> getInvStackList() {
        return content;
    }

    @Override
    protected void setInvStackList(DefaultedList<ItemStack> list) {
        content = list;
    }

    @Override
    public int size() {
        return MAX_INVENTORY_SIZE;
    }

    @Override
    public void onOpen(PlayerEntity player) {
        if (!this.removed && !player.isSpectator()) {
            this.stateManager.openChest(player, this.getWorld(), this.getPos(), this.getCachedState());
        }
    }

    @Override
    public void onClose(PlayerEntity player) {
        if (!this.removed && !player.isSpectator()) {
            this.stateManager.closeChest(player, this.getWorld(), this.getPos(), this.getCachedState());
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        if (!serializeLootTable(tag)) {
            Inventories.writeNbt(tag, content);
        }

        return tag;
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        content = DefaultedList.ofSize(size(), ItemStack.EMPTY);
        if (!deserializeLootTable(tag)) {
            Inventories.readNbt(tag, content);
        }
    }

    public void tick() {
        if (!this.removed) {
            this.stateManager.updateViewerCount(this.getWorld(), this.getPos(), this.getCachedState());
        }

        if (this.stateManager.getViewerCount() > 0) {
            scheduleTick();
        } else {
            BlockState blockstate = getCachedState();
            if (!(blockstate.getBlock() instanceof PantryBlock)) {
                markRemoved();
                return;
            }

            boolean flag = blockstate.get(PantryBlock.OPEN);
            if (flag) {
                playSound(blockstate, SoundEvents.BLOCK_BARREL_CLOSE);
                setOpen(blockstate, false);
            }
        }
    }

    private void scheduleTick() {
        Objects.requireNonNull(getWorld()).getBlockTickScheduler().schedule(getPos(), getCachedState().getBlock(), 5);
    }

    private void setOpen(BlockState state, boolean open) {
        Objects.requireNonNull(getWorld()).setBlockState(getPos(), state.with(PantryBlock.OPEN, open));
    }

    private void playSound(BlockState state, SoundEvent sound) {
        Vec3i vec3i = state.get(PantryBlock.FACING).getVector();
        BlockPos pos = getPos();
        double dX = (double) pos.getX() + .5d + (double) vec3i.getX() / 2.d;
        double dT = (double) pos.getY() + .5d + (double) vec3i.getY() / 2.d;
        double dZ = (double) pos.getZ() + .5d + (double) vec3i.getZ() / 2.d;
        World world = Objects.requireNonNull(getWorld());
        world.playSound(null, dX, dT, dZ, sound, SoundCategory.BLOCKS, .5f, world.getRandom().nextFloat() * .1f + .9f);
    }
}