package cr0s.warpdrive.block.energy;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IWarpTool;
import cr0s.warpdrive.block.BlockAbstractContainer;
import cr0s.warpdrive.client.ClientProxy;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.BlockProperties;
import cr0s.warpdrive.data.EnumComponentType;
import cr0s.warpdrive.data.EnumDisabledInputOutput;
import cr0s.warpdrive.data.EnumTier;
import cr0s.warpdrive.event.ModelBakeEventHandler;
import cr0s.warpdrive.item.ItemComponent;
import cr0s.warpdrive.render.BakedModelEnergyBank;
import ic2.api.energy.tile.IExplosionPowerOverride;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.common.property.Properties;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Optional.InterfaceList({
	@Optional.Interface(iface = "ic2.api.energy.tile.IExplosionPowerOverride", modid = "IC2")
})
public class BlockEnergyBank extends BlockAbstractContainer implements IExplosionPowerOverride {
	
	public static final IProperty<EnumDisabledInputOutput> CONFIG = PropertyEnum.create("config" , EnumDisabledInputOutput.class);
	
	public static final IUnlistedProperty<EnumDisabledInputOutput> DOWN  = Properties.toUnlisted(PropertyEnum.create("down" , EnumDisabledInputOutput.class));
	public static final IUnlistedProperty<EnumDisabledInputOutput> UP    = Properties.toUnlisted(PropertyEnum.create("up"   , EnumDisabledInputOutput.class));
	public static final IUnlistedProperty<EnumDisabledInputOutput> NORTH = Properties.toUnlisted(PropertyEnum.create("north", EnumDisabledInputOutput.class));
	public static final IUnlistedProperty<EnumDisabledInputOutput> SOUTH = Properties.toUnlisted(PropertyEnum.create("south", EnumDisabledInputOutput.class));
	public static final IUnlistedProperty<EnumDisabledInputOutput> WEST  = Properties.toUnlisted(PropertyEnum.create("west" , EnumDisabledInputOutput.class));
	public static final IUnlistedProperty<EnumDisabledInputOutput> EAST  = Properties.toUnlisted(PropertyEnum.create("east" , EnumDisabledInputOutput.class));
	
	public BlockEnergyBank(final String registryName) {
		super(registryName, Material.IRON);
		setUnlocalizedName("warpdrive.energy.EnergyBank.");
		hasSubBlocks = true;
		
		setDefaultState(getDefaultState()
		                .withProperty(BlockProperties.TIER, EnumTier.BASIC)
		                .withProperty(CONFIG, EnumDisabledInputOutput.DISABLED)
		);
		GameRegistry.registerTileEntity(TileEntityEnergyBank.class, WarpDrive.PREFIX + registryName);
	}
	
	@Nonnull
	@Override
	protected BlockStateContainer createBlockState() {
		return new ExtendedBlockState(this,
		                              new IProperty[] { BlockProperties.TIER, CONFIG },
		                              new IUnlistedProperty[] { DOWN, UP, NORTH, SOUTH, WEST, EAST });
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public IBlockState getStateFromMeta(int metadata) {
		return getDefaultState()
		       .withProperty(BlockProperties.TIER, EnumTier.get(metadata % 4));
	}
	
	@Override
	public int getMetaFromState(IBlockState blockState) {
		return blockState.getValue(BlockProperties.TIER).getIndex();
	}
	
	@Nonnull
	@Override
	public IBlockState getExtendedState(@Nonnull IBlockState blockState, IBlockAccess blockAccess, BlockPos blockPos) {
		if (!(blockState instanceof IExtendedBlockState)) {
			return blockState;
		}
		final TileEntity tileEntity = blockAccess.getTileEntity(blockPos);
		if (!(tileEntity instanceof TileEntityEnergyBank)) {
			return blockState;
		}
		final TileEntityEnergyBank tileEntityEnergyBank = (TileEntityEnergyBank) tileEntity;
		return ((IExtendedBlockState) blockState)
		       .withProperty(DOWN, tileEntityEnergyBank.getMode(EnumFacing.DOWN))
		       .withProperty(UP, tileEntityEnergyBank.getMode(EnumFacing.UP))
		       .withProperty(NORTH, tileEntityEnergyBank.getMode(EnumFacing.NORTH))
		       .withProperty(SOUTH, tileEntityEnergyBank.getMode(EnumFacing.SOUTH))
		       .withProperty(WEST, tileEntityEnergyBank.getMode(EnumFacing.WEST))
		       .withProperty(EAST, tileEntityEnergyBank.getMode(EnumFacing.EAST));
	}
	
	@SuppressWarnings("deprecation")
	@Nonnull
	@Override
	public IBlockState getActualState(@Nonnull final IBlockState blockState, final IBlockAccess blockAccess, final BlockPos pos) {
		return super.getActualState(blockState, blockAccess, pos);
	}
	
	@Nonnull
	@Override
	public TileEntity createNewTileEntity(@Nonnull World world, int metadata) {
		return new TileEntityEnergyBank((byte)(metadata % 4));
	}
	
	@Override
	public int damageDropped(IBlockState blockState) {
		return getMetaFromState(blockState);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void getSubBlocks(@Nonnull Item item, CreativeTabs creativeTab, List<ItemStack> list) {
		for (byte tier = 0; tier < 4; tier++) {
			ItemStack itemStack = new ItemStack(item, 1, tier);
			list.add(itemStack);
			if (tier > 0) {
				itemStack = new ItemStack(item, 1, tier);
				final NBTTagCompound tagCompound = new NBTTagCompound();
				tagCompound.setByte("tier", tier);
				tagCompound.setInteger("energy", WarpDriveConfig.ENERGY_BANK_MAX_ENERGY_STORED[tier - 1]);
				itemStack.setTagCompound(tagCompound);
				list.add(itemStack);
			}
		}
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public void modelInitialisation() {
		final Item item = Item.getItemFromBlock(this);
		ClientProxy.modelInitialisation(item);
		
		// Bind our TESR to our tile entity
		// ClientRegistry.bindTileEntitySpecialRenderer(TileEntityForceFieldProjector.class, new TileEntityForceFieldProjectorRenderer());
		
		// register (smart) baked model
		for (final EnumDisabledInputOutput enumDisabledInputOutput : CONFIG.getAllowedValues()) {
			for (final EnumTier enumTier : BlockProperties.TIER.getAllowedValues()) {
				final String variant = String.format("%s=%s,%s=%s",
				                                     CONFIG.getName(), enumDisabledInputOutput,
				                                     BlockProperties.TIER.getName(), enumTier);
				ModelBakeEventHandler.registerBakedModel(new ModelResourceLocation(getRegistryName(), variant), BakedModelEnergyBank.class);
			}
		}
	}
	
	@SideOnly(Side.CLIENT)
	@Override
	public byte getTier(final ItemStack itemStack) {
		if (itemStack == null || itemStack.getItem() != Item.getItemFromBlock(this)) {
			return 1;
		}
		final NBTTagCompound tagCompound = itemStack.getTagCompound();
		if (tagCompound != null && tagCompound.hasKey("tier")) {
			return tagCompound.getByte("tier");
		} else {
			return (byte) itemStack.getItemDamage();
		}
	}
	
	// IExplosionPowerOverride overrides
	@Override
	public boolean shouldExplode() {
		return false;
	}
	
	@Override
	public float getExplosionPower(int tier, float defaultPower) {
		return defaultPower;
	}
	
	@Override
	public boolean onBlockActivated(World world, BlockPos blockPos, IBlockState blockState, EntityPlayer entityPlayer, EnumHand hand, @Nullable ItemStack itemStackHeld, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if (world.isRemote) {
			return false;
		}
		
		final TileEntity tileEntity = world.getTileEntity(blockPos);
		if (!(tileEntity instanceof TileEntityEnergyBank)) {
			return false;
		}
		final TileEntityEnergyBank tileEntityEnergyBank = (TileEntityEnergyBank) tileEntity;
		
		if (itemStackHeld != null && itemStackHeld.getItem() instanceof IWarpTool) {
			if (entityPlayer.isSneaking()) {
				tileEntityEnergyBank.setMode(facing, tileEntityEnergyBank.getMode(facing).getPrevious());
			} else {
				tileEntityEnergyBank.setMode(facing, tileEntityEnergyBank.getMode(facing).getNext());
			}
			final ItemStack itemStack = new ItemStack(Item.getItemFromBlock(this), 1, getMetaFromState(blockState));
			switch (tileEntityEnergyBank.getMode(facing)) {
				case INPUT:
					Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.guide.prefix")
					    .appendSibling(new TextComponentTranslation(itemStack.getUnlocalizedName() + ".name"))
					    .appendSibling(new TextComponentTranslation("warpdrive.energy.side.changedToInput", facing.name())) );
					return true;
				case OUTPUT:
					Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.guide.prefix")
					    .appendSibling(new TextComponentTranslation(itemStack.getUnlocalizedName() + ".name"))
					    .appendSibling(new TextComponentTranslation("warpdrive.energy.side.changedToOutput", facing.name())) );
					return true;
				case DISABLED:
				default:
					Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.guide.prefix")
					    .appendSibling(new TextComponentTranslation(itemStack.getUnlocalizedName() + ".name"))
					    .appendSibling(new TextComponentTranslation("warpdrive.energy.side.changedToDisabled", facing.name())) );
					return true;
			}
		}
		
		EnumComponentType enumComponentType = null;
		if (itemStackHeld != null && itemStackHeld.getItem() instanceof ItemComponent) {
			enumComponentType = EnumComponentType.get(itemStackHeld.getItemDamage());
		}
		
		// sneaking with an empty hand or an upgrade item in hand to dismount current upgrade
		if (entityPlayer.isSneaking()) {
			// using an upgrade item or an empty means dismount upgrade
			if (itemStackHeld == null || enumComponentType != null) {
				// find a valid upgrade to dismount
				if (itemStackHeld == null || !tileEntityEnergyBank.hasUpgrade(enumComponentType)) {
					enumComponentType = (EnumComponentType)tileEntityEnergyBank.getFirstUpgradeOfType(EnumComponentType.class, null);
				}
				
				if (enumComponentType == null) {
					// no more upgrades to dismount
					Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.noUpgradeToDismount"));
					return true;
				}
				
				if (!entityPlayer.capabilities.isCreativeMode) {
					// dismount the current upgrade item
					ItemStack itemStackDrop = ItemComponent.getItemStackNoCache(enumComponentType, 1);
					EntityItem entityItem = new EntityItem(world, entityPlayer.posX, entityPlayer.posY + 0.5D, entityPlayer.posZ, itemStackDrop);
					entityItem.setNoPickupDelay();
					world.spawnEntityInWorld(entityItem);
				}
				
				tileEntityEnergyBank.dismountUpgrade(enumComponentType);
				// upgrade dismounted
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.dismounted", enumComponentType.name()));
				return false;
				
			}
			
		} else if (itemStackHeld == null) {// no sneaking and no item in hand => show status
			Commons.addChatMessage(entityPlayer, tileEntityEnergyBank.getStatus());
			return true;
			
		} else if (enumComponentType != null) {// no sneaking and an upgrade in hand => mounting an upgrade
			// validate type
			if (tileEntityEnergyBank.getUpgradeMaxCount(enumComponentType) <= 0) {
				// invalid upgrade type
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.invalidUpgrade"));
				return true;
			}
			if (!tileEntityEnergyBank.canUpgrade(enumComponentType)) {
				// too many upgrades
				Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.tooManyUpgrades",
					tileEntityEnergyBank.getUpgradeMaxCount(enumComponentType)));
				return true;
			}
			
			if (!entityPlayer.capabilities.isCreativeMode) {
				// validate quantity
				if (itemStackHeld.stackSize < 1) {
					// not enough upgrade items
					Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.notEnoughUpgrades"));
					return true;
				}
				
				// update player inventory
				itemStackHeld.stackSize -= 1;
			}
			
			// mount the new upgrade item
			tileEntityEnergyBank.mountUpgrade(enumComponentType);
			// upgrade mounted
			Commons.addChatMessage(entityPlayer, new TextComponentTranslation("warpdrive.upgrade.result.mounted", enumComponentType.name()));
		}
		
		return false;
	}
}