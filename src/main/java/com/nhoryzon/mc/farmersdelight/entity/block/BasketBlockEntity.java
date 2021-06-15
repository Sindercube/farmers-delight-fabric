package com.nhoryzon.mc.farmersdelight.entity.block;

import com.nhoryzon.mc.farmersdelight.FarmersDelightMod;
import com.nhoryzon.mc.farmersdelight.block.BasketBlock;
import com.nhoryzon.mc.farmersdelight.registry.BlockEntityTypesRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BasketBlockEntity extends LootableContainerBlockEntity implements Basket {
    private static final int MAX_INVENTORY_SIZE = 27;
    private DefaultedList<ItemStack> content;
    private int transferCooldown = -1;

    protected BasketBlockEntity(BlockEntityType<?> type, BlockPos blockPos, BlockState blockState) {
        super(type, blockPos, blockState);
        this.content = DefaultedList.ofSize(MAX_INVENTORY_SIZE, ItemStack.EMPTY);
    }

    public static boolean pullItems(World world, Basket basket, int facingIndex) {
        for (ItemEntity itementity : getCaptureItems(world, basket, facingIndex)) {
            if (captureItem(basket, itementity)) {
                return true;
            }
        }

        return false;
    }

    public static ItemStack putStackInInventoryAllSlots(Inventory destination, ItemStack stack) {
        int i = destination.size();

        for (int j = 0; j < i && !stack.isEmpty(); ++j) {
            stack = insertStack(destination, stack, j);
        }

        return stack;
    }

    private static boolean canInsertItemInSlot(Inventory inventoryIn, ItemStack stack, int index) {
        if (!inventoryIn.isValid(index, stack)) {
            return false;
        } else {
            return !(inventoryIn instanceof SidedInventory) || ((SidedInventory) inventoryIn).canInsert(index, stack, null);
        }
    }

    private static boolean canCombine(ItemStack stack1, ItemStack stack2) {
        if (stack1.getItem() != stack2.getItem()) {
            return false;
        } else if (stack1.getDamage() != stack2.getDamage()) {
            return false;
        } else if (stack1.getCount() > stack1.getMaxCount()) {
            return false;
        } else {
            return ItemStack.areTagsEqual(stack1, stack2);
        }
    }

    private static ItemStack insertStack(Inventory destination, ItemStack stack, int index) {
        ItemStack itemstack = destination.getStack(index);
        if (canInsertItemInSlot(destination, stack, index)) {
            boolean flag = false;
            boolean isDestinationEmpty = destination.isEmpty();
            if (itemstack.isEmpty()) {
                destination.setStack(index, stack);
                stack = ItemStack.EMPTY;
                flag = true;
            } else if (canCombine(itemstack, stack)) {
                int i = stack.getMaxCount() - itemstack.getCount();
                int j = Math.min(stack.getCount(), i);
                stack.decrement(j);
                itemstack.increment(j);
                flag = j > 0;
            }

            if (flag) {
                if (isDestinationEmpty && destination instanceof BasketBlockEntity firstBasket) {
                    if (!firstBasket.mayTransfer()) {
                        int k = 0;

                        firstBasket.setTransferCooldown(8 - k);
                    }
                }

                destination.markDirty();
            }
        }

        return stack;
    }

    public static boolean captureItem(Inventory inventory, ItemEntity itemEntity) {
        boolean flag = false;
        ItemStack groundItemStack = itemEntity.getStack().copy();
        ItemStack itemStackCatch = putStackInInventoryAllSlots(inventory, groundItemStack);
        if (itemStackCatch.isEmpty()) {
            flag = true;
            itemEntity.remove(Entity.RemovalReason.DISCARDED);
        } else {
            itemEntity.setStack(itemStackCatch);
        }

        return flag;
    }

    public static List<ItemEntity> getCaptureItems(World world, Basket basket, int facingIndex) {
        return world == null ? new ArrayList<>() : basket.getFacingCollectionArea(facingIndex).getBoundingBoxes().stream()
                .flatMap((boundingBoxe) -> world.getEntitiesByClass(ItemEntity.class,
                        boundingBoxe.offset(basket.getHopperX() - .5d, basket.getHopperY() - .5d, basket.getHopperZ() - .5d),
                        EntityPredicates.VALID_ENTITY).stream()).collect(Collectors.toList());
    }

    public BasketBlockEntity(BlockPos blockPos, BlockState blockState) {
        this(BlockEntityTypesRegistry.BASKET.get(), blockPos, blockState);
    }

    @Override
    protected DefaultedList<ItemStack> getInvStackList() {
        return content;
    }

    @Override
    protected void setInvStackList(DefaultedList<ItemStack> content) {
        this.content = content;
    }

    @Override
    protected Text getContainerName() {
        return FarmersDelightMod.i18n("container.basket");
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
    }

    @Override
    public int size() {
        return content.size();
    }

    @Override
    public double getHopperX() {
        return getPos().getX() + .5d;
    }

    @Override
    public double getHopperY() {
        return getPos().getY() + .5d;
    }

    @Override
    public double getHopperZ() {
        return getPos().getZ() + .5d;
    }

    public static void tick(World world, BlockPos pos, BlockState state, BasketBlockEntity blockEntity) {
        if (world != null && !world.isClient()) {
            --blockEntity.transferCooldown;
            if (blockEntity.isNotInTransferCooldown()) {
                blockEntity.setTransferCooldown(0);
                int facing = blockEntity.getCachedState().get(BasketBlock.FACING).getId();
                blockEntity.updateHopper(() -> pullItems(world, blockEntity, facing));
            }
        }
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        checkLootInteraction(null);
        return Inventories.splitStack(getInvStackList(), slot, amount);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        checkLootInteraction(null);
        getInvStackList().set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        if (!serializeLootTable(tag)) {
            Inventories.writeNbt(tag, content);
        }
        tag.putInt("TransferCooldown", transferCooldown);

        return tag;
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        content = DefaultedList.ofSize(size(), ItemStack.EMPTY);
        if (!deserializeLootTable(tag)) {
            Inventories.readNbt(tag, content);
        }
        transferCooldown = tag.getInt("TransferCooldown");
    }

    public void setTransferCooldown(int ticks) {
        transferCooldown = ticks;
    }

    private boolean isNotInTransferCooldown() {
        return transferCooldown <= 0;
    }

    public boolean mayTransfer() {
        return transferCooldown > 8;
    }

    private void updateHopper(Supplier<Boolean> supplier) {
        if (world != null && !world.isClient()) {
            if (isNotInTransferCooldown() && getCachedState().get(BasketBlock.ENABLED)) {
                boolean flag = false;
                if (!isFull()) {
                    flag = supplier.get();
                }

                if (flag) {
                    setTransferCooldown(8);
                    markDirty();
                }
            }
        }
    }

    private boolean isFull() {
        for (ItemStack itemstack : content) {
            if (itemstack.isEmpty() || itemstack.getCount() != itemstack.getMaxCount()) {
                return false;
            }
        }

        return true;
    }
}