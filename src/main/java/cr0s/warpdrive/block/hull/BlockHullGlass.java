package cr0s.warpdrive.block.hull;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IBlockBase;
import cr0s.warpdrive.api.IDamageReceiver;
import cr0s.warpdrive.client.ClientProxy;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.Vector3;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.BlockColored;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockHullGlass extends BlockColored implements IBlockBase, IDamageReceiver {
	
	final byte tier;
	
	public BlockHullGlass(final String registryName, final byte tier) {
		super(Material.GLASS);
		this.tier = tier;
		setHardness(WarpDriveConfig.HULL_HARDNESS[tier - 1]);
		setResistance(WarpDriveConfig.HULL_BLAST_RESISTANCE[tier - 1] * 5 / 3);
		setSoundType(SoundType.GLASS);
		setCreativeTab(WarpDrive.creativeTabWarpDrive);
		setUnlocalizedName("warpdrive.hull" + tier + ".glass.");
		setRegistryName(registryName);
		WarpDrive.register(this, new ItemBlockHull(this));
		
		setLightLevel(10.0F / 15.0F);
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public EnumPushReaction getMobilityFlag(IBlockState state) {
		return EnumPushReaction.BLOCK;
	}
	
	@Override
	public boolean isVisuallyOpaque() {
		return false;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}
	
	@Override
	public byte getTier(ItemStack itemStack) {
		return tier;
	}
	
	@Override
	public EnumRarity getRarity(final ItemStack itemStack, final EnumRarity rarity) {
		switch (getTier(itemStack)) {
		case 0:	return EnumRarity.EPIC;
		case 1:	return EnumRarity.COMMON;
		case 2:	return EnumRarity.UNCOMMON;
		case 3:	return EnumRarity.RARE;
		default: return rarity;
		}
	}
	
	@Nullable
	@Override
	public ItemBlock createItemBlock() {
		return new ItemBlockHull(this);
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public void modelInitialisation() {
		Item item = Item.getItemFromBlock(this);
		ClientProxy.modelInitialisation(item);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean isFullyOpaque(IBlockState state) {
		return false;
	}
	
	@SuppressWarnings("deprecation")
	@SideOnly(Side.CLIENT)
	@Override
	public boolean shouldSideBeRendered(IBlockState blockState, @Nonnull IBlockAccess blockAccess, @Nonnull BlockPos blockPos, EnumFacing facing) {
		BlockPos blockPosSide = blockPos.offset(facing);
		if (blockAccess.isAirBlock(blockPosSide)) {
			return true;
		}
		EnumFacing opposite = facing.getOpposite();
		IBlockState blockStateSide = blockAccess.getBlockState(blockPosSide);
		if ( blockStateSide.getBlock() instanceof BlockGlass
		  || blockStateSide.getBlock() instanceof BlockHullGlass ) {
			return blockState.getBlock().getMetaFromState(blockState)
				!= blockStateSide.getBlock().getMetaFromState(blockStateSide);
		}
		return !blockAccess.isSideSolid(blockPosSide, opposite, false);
	}
	
	@Override
	public float getBlockHardness(IBlockState blockState, World world, BlockPos blockPos, DamageSource damageSource, int damageParameter, Vector3 damageDirection, int damageLevel) {
		// TODO: adjust hardness to damage type/color
		return WarpDriveConfig.HULL_HARDNESS[tier - 1];
	}
	
	@Override
	public int applyDamage(IBlockState blockState, World world, BlockPos blockPos,
	                       DamageSource damageSource, int damageParameter, Vector3 damageDirection, int damageLevel) {
		if (damageLevel <= 0) {
			return 0;
		}
		if (tier == 1) {
			world.setBlockToAir(blockPos);
		} else {
			world.setBlockState(blockPos, WarpDrive.blockHulls_glass[tier - 2]
			                              .getDefaultState()
			                              .withProperty(COLOR, blockState.getValue(COLOR)), 2);
		}
		return 0;
	}
}
