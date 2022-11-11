package net.geforcemods.securitycraft.api;

import java.util.ArrayList;

import net.geforcemods.securitycraft.util.ITickingBlockEntity;
import net.geforcemods.securitycraft.util.LevelUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class LinkableBlockEntity extends CustomizableBlockEntity implements ITickingBlockEntity {
	public ArrayList<LinkedBlock> linkedBlocks = new ArrayList<>();
	private ListTag nbtTagStorage = null;

	public LinkableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void tick(Level level, BlockPos pos, BlockState state) {
		if (hasLevel() && nbtTagStorage != null) {
			readLinkedBlocks(nbtTagStorage);
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
			nbtTagStorage = null;
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);

		if (tag.contains("linkedBlocks")) {
			if (!hasLevel()) {
				nbtTagStorage = tag.getList("linkedBlocks", Tag.TAG_COMPOUND);
				return;
			}

			readLinkedBlocks(tag.getList("linkedBlocks", Tag.TAG_COMPOUND));
		}
	}

	@Override
	public void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);

		if (hasLevel() && linkedBlocks.size() > 0) {
			ListTag tagList = new ListTag();

			LevelUtils.addScheduledTask(level, () -> {
				for (int i = linkedBlocks.size() - 1; i >= 0; i--) {
					LinkedBlock block = linkedBlocks.get(i);
					CompoundTag toAppend = new CompoundTag();

					if (block != null) {
						if (!block.validate(level)) {
							linkedBlocks.remove(i);
							continue;
						}

						toAppend.putString("blockName", block.blockName);
						toAppend.putInt("blockX", block.getX());
						toAppend.putInt("blockY", block.getY());
						toAppend.putInt("blockZ", block.getZ());
					}

					tagList.add(toAppend);
				}

				tag.put("linkedBlocks", tagList);
			});
		}
	}

	@Override
	public void setRemoved() {
		for (LinkedBlock block : linkedBlocks) {
			LinkableBlockEntity.unlink(block.asBlockEntity(level), this);
		}
	}

	@Override
	public void onOptionChanged(Option<?> option) {
		createLinkedBlockAction(new ILinkedAction.OptionChanged(option), this);
		super.onOptionChanged(option);
	}

	private void readLinkedBlocks(ListTag list) {
		for (int i = 0; i < list.size(); i++) {
			String name = list.getCompound(i).getString("blockName");
			int x = list.getCompound(i).getInt("blockX");
			int y = list.getCompound(i).getInt("blockY");
			int z = list.getCompound(i).getInt("blockZ");
			LinkedBlock block = new LinkedBlock(name, new BlockPos(x, y, z));

			if (hasLevel() && !block.validate(level)) {
				list.remove(i);
				continue;
			}

			if (!linkedBlocks.contains(block))
				link(this, block.asBlockEntity(level));
		}
	}

	/**
	 * Links two blocks together. Calls onLinkedBlockAction() whenever certain events (found in {@link ILinkedAction}) occur.
	 */
	public static void link(LinkableBlockEntity blockEntity1, LinkableBlockEntity blockEntity2) {
		if (isLinkedWith(blockEntity1, blockEntity2))
			return;

		LinkedBlock block1 = new LinkedBlock(blockEntity1);
		LinkedBlock block2 = new LinkedBlock(blockEntity2);

		if (!blockEntity1.linkedBlocks.contains(block2)) {
			blockEntity1.linkedBlocks.add(block2);
			blockEntity1.setChanged();
		}

		if (!blockEntity2.linkedBlocks.contains(block1)) {
			blockEntity2.linkedBlocks.add(block1);
			blockEntity2.setChanged();
		}
	}

	/**
	 * Unlinks the second block entity from the first.
	 *
	 * @param blockEntity1 The block entity to unlink from
	 * @param blockEntity2 The block entity to unlink
	 */
	public static void unlink(LinkableBlockEntity blockEntity1, LinkableBlockEntity blockEntity2) {
		if (blockEntity1 == null || blockEntity2 == null)
			return;

		LinkedBlock block = new LinkedBlock(blockEntity2);

		if (blockEntity1.linkedBlocks.contains(block)) {
			blockEntity1.linkedBlocks.remove(block);
			blockEntity1.setChanged();
		}
	}

	/**
	 * @return Are the two blocks linked together?
	 */
	public static boolean isLinkedWith(LinkableBlockEntity blockEntity1, LinkableBlockEntity blockEntity2) {
		return blockEntity1.linkedBlocks.contains(new LinkedBlock(blockEntity2)) && blockEntity2.linkedBlocks.contains(new LinkedBlock(blockEntity1));
	}

	/**
	 * Calls onLinkedBlockAction() for every block this block entity is linked to. <p> <b>NOTE:</b> Never use this method in
	 * onLinkedBlockAction(), use createLinkedBlockAction(EnumLinkedAction, Object[], ArrayList[LinkableBlockEntity] instead.
	 *
	 * @param action The action that occurred
	 * @param excludedBE The LinkableBlockEntity which called this method, prevents infinite loops.
	 */
	public void createLinkedBlockAction(ILinkedAction action, LinkableBlockEntity excludedBE) {
		ArrayList<LinkableBlockEntity> list = new ArrayList<>();

		list.add(excludedBE);
		createLinkedBlockAction(action, list);
	}

	/**
	 * Calls onLinkedBlockAction() for every block this block entity is linked to.
	 *
	 * @param action The action that occurred
	 * @param excludedBEs LinkableBlockEntities that shouldn't have onLinkedBlockAction() called on them, prevents infinite
	 *            loops. Always add your block entity to the list whenever using this method
	 */
	public void createLinkedBlockAction(ILinkedAction action, ArrayList<LinkableBlockEntity> excludedBEs) {
		for (LinkedBlock block : linkedBlocks) {
			if (!excludedBEs.contains(block.asBlockEntity(level))) {
				BlockState state = level.getBlockState(block.getPos());

				block.asBlockEntity(level).onLinkedBlockAction(action, excludedBEs);
				level.sendBlockUpdated(block.getPos(), state, state, 3);
			}
		}
	}

	/**
	 * Called whenever certain actions occur in blocks this block entity is linked to. See {@link ILinkedAction} for
	 * parameter descriptions. <p>
	 *
	 * @param action The {@link ILinkedAction} that occurred
	 * @param excludedBEs LinkableBlockEntities that aren't going to have onLinkedBlockAction() called on them, always add
	 *            your block entity to the list if you're going to call createLinkedBlockAction() in this method to
	 *            chain-link multiple blocks (i.e: like Laser Blocks)
	 */
	protected void onLinkedBlockAction(ILinkedAction action, ArrayList<LinkableBlockEntity> excludedBEs) {}
}
