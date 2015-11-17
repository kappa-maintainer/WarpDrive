package cr0s.warpdrive.block.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.util.ForgeDirection;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.Vector3;
import cr0s.warpdrive.data.VectorI;
import cr0s.warpdrive.network.PacketHandler;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;

public class TileEntityLaserTreeFarm extends TileEntityAbstractMiner {
	private boolean breakLeaves = false;
	private boolean tapTrees = false;
	
	private boolean isFarming() {
		return currentState != STATE_IDLE;
	}
	private final int STATE_IDLE = 0;
	private final int STATE_WARMUP = 1;
	private final int STATE_SCAN = 2;
	private final int STATE_HARVEST = 3;
	private final int STATE_TAP = 4;
	private final int STATE_PLANT = 5;
	private int currentState = STATE_IDLE;
	
	private boolean enoughPower = false;
	
	private final int TREE_FARM_MAX_MEDIUMS_COUNT = 1;
	private final int TREE_FARM_MAX_LOG_DISTANCE = 8;
	private final int TREE_FARM_MAX_LOG_DISTANCE_PER_MEDIUM = 4;
	private final int TREE_FARM_MAX_RADIUS_PER_MEDIUM = 2;
	
	private final int TREE_FARM_WARMUP_DELAY_TICKS = 40;
	private final int TREE_FARM_SCAN_DELAY_TICKS = 40;
	private final int TREE_FARM_HARVEST_LOG_DELAY_TICKS = 4;
	private final int TREE_FARM_BREAK_LEAF_DELAY_TICKS = 4;
	private final int TREE_FARM_SILKTOUCH_LEAF_DELAY_TICKS = 4;
	private final int TREE_FARM_TAP_TREE_WET_DELAY_TICKS = 4;
	private final int TREE_FARM_TAP_TREE_DRY_DELAY_TICKS = 1;
	private final int TREE_FARM_PLANT_DELAY_TICKS = 1;
	private final int TREE_FARM_LOWPOWER_DELAY_TICKS = 40;
	
	private final int TREE_FARM_ENERGY_PER_SURFACE = 1;
	private final int TREE_FARM_ENERGY_PER_WET_SPOT = 1;
	private final double TREE_FARM_ENERGY_PER_LOG = 1;
	private final double TREE_FARM_ENERGY_PER_LEAF = 1;
	private final double TREE_FARM_SILKTOUCH_ENERGY_FACTOR = 2.0D;
	private final int TREE_FARM_ENERGY_PER_SAPLING = 1;
	
	private int delayTargetTicks = 0;
	
	private int totalHarvested = 0;
	
	private boolean bScanOnReload = false;
	private int delayTicks = 0;
	
	private final int RADIUS_DEFAULT = 8;
	private int radiusX = RADIUS_DEFAULT;
	private int radiusZ = RADIUS_DEFAULT;
	
	LinkedList<VectorI> soils;
	private int soilIndex = 0;
	ArrayList<VectorI> valuables;
	private int valuableIndex = 0;
	
	public TileEntityLaserTreeFarm() {
		super();
		laserOutputSide = ForgeDirection.UP;
		peripheralName = "warpdriveLaserTreefarm";
		addMethods(new String[] {
				"start",
				"stop",
				"radius",
				"state",
				"breakLeaves",
				"silktouch",
				"tapTrees"
		});
		laserMediumMaxCount = 3 + 0 * TREE_FARM_MAX_MEDIUMS_COUNT;
	}
	
	@Override
	public void updateEntity() {
		super.updateEntity();
		
		if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
			return;
		}
		
		if (bScanOnReload) {
			soils = scanSoils();
			valuables = new ArrayList<VectorI>(scanTrees());
			bScanOnReload = false;
			return;
		}
		
		if (currentState == STATE_IDLE) {
			delayTicks = 0;
			delayTargetTicks = TREE_FARM_WARMUP_DELAY_TICKS;
			updateMetadata(BlockLaserTreeFarm.ICON_IDLE);
			
			// force start if no computer control is available
			if (!WarpDriveConfig.isComputerCraftLoaded && !WarpDriveConfig.isOpenComputersLoaded) {
				breakLeaves = true;
				enableSilktouch = false;
				tapTrees = true;
				start(null);
			}
			return;
		}
		
		delayTicks++;
		
		// Scanning
		if (currentState == STATE_WARMUP) {
			updateMetadata(BlockLaserTreeFarm.ICON_SCANNINGLOWPOWER);
			if (delayTicks >= delayTargetTicks) {
				delayTicks = 0;
				delayTargetTicks = TREE_FARM_SCAN_DELAY_TICKS;
				currentState = STATE_SCAN;
				updateMetadata(BlockLaserTreeFarm.ICON_SCANNINGLOWPOWER);
				return;
			}
		} else if (currentState == STATE_SCAN) {
			int energyCost = TREE_FARM_ENERGY_PER_SURFACE * (1 + 2 * radiusX) * (1 + 2 * radiusZ);
			if (delayTicks == 1) {
				if (WarpDriveConfig.LOGGING_COLLECTION) {
					WarpDrive.logger.debug("Scan pre-tick");
				}
				// check power level
				enoughPower = consumeEnergyFromLaserMediums(energyCost, true);
				if (!enoughPower) {
					currentState = STATE_WARMUP;	// going back to warmup state to show the animation when it'll be back online
					delayTicks = 0;
					delayTargetTicks = TREE_FARM_LOWPOWER_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_SCANNINGLOWPOWER);
					return;
				} else {
					updateMetadata(BlockLaserTreeFarm.ICON_SCANNINGPOWERED);
				}
				
				// show current layer
				int age = Math.max(40, 2 * TREE_FARM_SCAN_DELAY_TICKS);
				double xmax = xCoord + radiusX + 1.0D;
				double xmin = xCoord - radiusX + 0.0D;
				double zmax = zCoord + radiusZ + 1.0D;
				double zmin = zCoord - radiusZ + 0.0D;
				double y = yCoord + worldObj.rand.nextInt(9);
				PacketHandler.sendBeamPacket(worldObj, new Vector3(xmin, y, zmin), new Vector3(xmax, y, zmin), 0.3F, 0.0F, 1.0F, age, 0, 50);
				PacketHandler.sendBeamPacket(worldObj, new Vector3(xmax, y, zmin), new Vector3(xmax, y, zmax), 0.3F, 0.0F, 1.0F, age, 0, 50);
				PacketHandler.sendBeamPacket(worldObj, new Vector3(xmax, y, zmax), new Vector3(xmin, y, zmax), 0.3F, 0.0F, 1.0F, age, 0, 50);
				PacketHandler.sendBeamPacket(worldObj, new Vector3(xmin, y, zmax), new Vector3(xmin, y, zmin), 0.3F, 0.0F, 1.0F, age, 0, 50);
				
			} else if (delayTicks >= delayTargetTicks) {
				if (WarpDriveConfig.LOGGING_COLLECTION) {
					WarpDrive.logger.debug("Scan tick");
				}
				delayTicks = 0;
				
				// consume power
				enoughPower = consumeEnergyFromLaserMediums(energyCost, false);
				if (!enoughPower) {
					delayTargetTicks = TREE_FARM_LOWPOWER_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_SCANNINGLOWPOWER);
					return;
				} else {
					delayTargetTicks = TREE_FARM_SCAN_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_SCANNINGPOWERED);
				}
				
				// scan
				soils = scanSoils();
				soilIndex = 0;
				
				valuables = new ArrayList<VectorI>(scanTrees());
				valuableIndex = 0;
				if (valuables != null && valuables.size() > 0) {
					worldObj.playSoundEffect(xCoord + 0.5f, yCoord, zCoord + 0.5f, "warpdrive:hilaser", 4F, 1F);
					currentState = tapTrees ? STATE_TAP : STATE_HARVEST;
					delayTargetTicks = TREE_FARM_HARVEST_LOG_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_FARMINGPOWERED);
					return;
					
				} else if (soils != null && soils.size() > 0) {
					worldObj.playSoundEffect(xCoord + 0.5f, yCoord, zCoord + 0.5f, "warpdrive:hilaser", 4F, 1F);
					currentState = STATE_PLANT;
					delayTargetTicks = TREE_FARM_PLANT_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_PLANTINGPOWERED);
					return;
					
				} else {
					worldObj.playSoundEffect(xCoord + 0.5f, yCoord, zCoord + 0.5f, "warpdrive:lowlaser", 4F, 1F);
					currentState = STATE_WARMUP;
					delayTargetTicks = TREE_FARM_WARMUP_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_SCANNINGLOWPOWER);
				}
			}
		} else if (currentState == STATE_HARVEST || currentState == STATE_TAP) {
			if (delayTicks >= delayTargetTicks) {
				if (WarpDriveConfig.LOGGING_COLLECTION) {
					WarpDrive.logger.debug("Harvest/tap tick");
				}
				delayTicks = 0;
				
				// harvesting done => plant
				if (valuables == null || valuableIndex >= valuables.size()) {
					valuableIndex = 0;
					currentState = STATE_PLANT;
					delayTargetTicks = TREE_FARM_PLANT_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_PLANTINGPOWERED);
					return;
				}
				
				// get current block
				VectorI valuable = valuables.get(valuableIndex);
				Block block = worldObj.getBlock(valuable.x, valuable.y, valuable.z);
				valuableIndex++;
				boolean isLog = isLog(block);
				boolean isLeaf = isLeaf(block);
				
				// save the rubber producing blocks in tapping mode
				if (currentState == STATE_TAP) {
					if (block.isAssociatedBlock(WarpDriveConfig.IC2_rubberWood)) {
						int metadata = worldObj.getBlockMetadata(valuable.x, valuable.y, valuable.z);
						if (metadata >= 2 && metadata <= 5) {
							if (WarpDriveConfig.LOGGING_COLLECTION) {
								WarpDrive.logger.debug("Tap found rubber wood wetspot at " + valuable + " with metadata " + metadata);
							}
							
							// consume power
							int energyCost = TREE_FARM_ENERGY_PER_WET_SPOT;
							enoughPower = consumeEnergyFromLaserMediums(energyCost, false);
							if (!enoughPower) {
								delayTargetTicks = TREE_FARM_LOWPOWER_DELAY_TICKS;
								updateMetadata(BlockLaserTreeFarm.ICON_FARMINGLOWPOWER);
								return;
							} else {
								delayTargetTicks = TREE_FARM_TAP_TREE_WET_DELAY_TICKS;
								updateMetadata(BlockLaserTreeFarm.ICON_FARMINGPOWERED);
							}
							
							ItemStack resin = WarpDriveConfig.IC2_Resin.copy();
							resin.stackSize = (int) Math.round(Math.random() * 4);
							if (addToConnectedInventory(resin)) {
								stop();
							}
							totalHarvested += resin.stackSize;
							int age = Math.max(10, Math.round((4 + worldObj.rand.nextFloat()) * WarpDriveConfig.MINING_LASER_MINE_DELAY_TICKS));
							PacketHandler.sendBeamPacket(worldObj, laserOutput, new Vector3(valuable.x, valuable.y, valuable.z).translate(0.5D),
									0.8F, 0.8F, 0.2F, age, 0, 50);
							
							worldObj.setBlockMetadataWithNotify(valuable.x, valuable.y, valuable.z, metadata + 6, 3);
							// done with this block
							return;
						} else if (metadata != 0 && metadata != 1) {
							delayTargetTicks = TREE_FARM_TAP_TREE_DRY_DELAY_TICKS;
							// done with this block
							return;
						}
					}
				}
				
				if (isLog || (breakLeaves && isLeaf)) {// actually break the block?
					// consume power
					double energyCost = isLog ? TREE_FARM_ENERGY_PER_LOG : TREE_FARM_ENERGY_PER_LEAF;
					if (enableSilktouch) {
						energyCost *= TREE_FARM_SILKTOUCH_ENERGY_FACTOR;
					}
					enoughPower = consumeEnergyFromLaserMediums((int) Math.round(energyCost), false);
					if (!enoughPower) {
						delayTargetTicks = TREE_FARM_LOWPOWER_DELAY_TICKS;
						updateMetadata(BlockLaserTreeFarm.ICON_FARMINGLOWPOWER);
						return;
					} else {
						delayTargetTicks = isLog ? TREE_FARM_HARVEST_LOG_DELAY_TICKS : enableSilktouch ? TREE_FARM_SILKTOUCH_LEAF_DELAY_TICKS : TREE_FARM_BREAK_LEAF_DELAY_TICKS;
						updateMetadata(BlockLaserTreeFarm.ICON_FARMINGPOWERED);
					}
					
					totalHarvested++;
					int age = Math.max(10, Math.round((4 + worldObj.rand.nextFloat()) * WarpDriveConfig.MINING_LASER_MINE_DELAY_TICKS));
					PacketHandler.sendBeamPacket(worldObj, laserOutput, new Vector3(valuable.x, valuable.y, valuable.z).translate(0.5D),
							0.2F, 0.7F, 0.4F, age, 0, 50);
					worldObj.playSoundEffect(xCoord + 0.5f, yCoord, zCoord + 0.5f, "warpdrive:lowlaser", 4F, 1F);
					
					harvestBlock(valuable);
				}
			}
		} else if (currentState == STATE_PLANT) {
			if (delayTicks >= delayTargetTicks) {
				if (WarpDriveConfig.LOGGING_COLLECTION) {
					WarpDrive.logger.debug("Plant final tick");
				}
				delayTicks = 0;
				
				// planting done => scan
				if (soils == null || soilIndex >= soils.size()) {
					soilIndex = 0;
					currentState = STATE_SCAN;
					delayTargetTicks = TREE_FARM_SCAN_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_SCANNINGPOWERED);
					return;
				}
				
				// get current block
				VectorI soil = soils.get(soilIndex);
				Block block = worldObj.getBlock(soil.x, soil.y, soil.z);
				soilIndex++;
				IInventory inventory = getFirstConnectedInventory();
				if (inventory == null) {
					currentState = STATE_WARMUP;
					delayTargetTicks = TREE_FARM_WARMUP_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_SCANNINGLOWPOWER);
					return;
				}
				
				int slotIndex = 0;
				boolean found = false;
				int plantableCount = 0;
				ItemStack itemStack = null;
				while (slotIndex < inventory.getSizeInventory() && !found) {
					itemStack = inventory.getStackInSlot(slotIndex);
					if (itemStack != null) {
						WarpDrive.logger.debug("- " + slotIndex + ": " + itemStack + " => " + itemStack.getItem() + " Plantable: " + (itemStack.getItem() instanceof IPlantable) + " Growable: " + (itemStack.getItem() instanceof IGrowable));
					}
					if (itemStack == null || itemStack.stackSize <= 0 || !(itemStack.getItem() instanceof IPlantable)) {
						slotIndex++;
						continue;
					}
					plantableCount++;
					WarpDrive.logger.debug("IPlantable found: " + itemStack);
					
					if (!block.canSustainPlant(worldObj, soil.x, soil.y, soil.z, ForgeDirection.UP, (IPlantable)itemStack.getItem())) {
						slotIndex++;
						continue;
					}
					
					found = true;
				}
				
				// no plantable found at all, back to scanning
				if (plantableCount <= 0) {
					currentState = STATE_SCAN;
					delayTargetTicks = TREE_FARM_SCAN_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_SCANNINGPOWERED);
					return;
				}
				
				// no sapling found for this soil, moving on...
				if (!found || itemStack == null) {
					WarpDrive.logger.debug("No sapling found");
					return;
				}
				WarpDrive.logger.debug("Sapling found: " + itemStack);
				
				// consume power
				double energyCost = TREE_FARM_ENERGY_PER_SAPLING;
				enoughPower = consumeEnergyFromLaserMediums((int) Math.round(energyCost), false);
				if (!enoughPower) {
					delayTargetTicks = TREE_FARM_LOWPOWER_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_PLANTINGLOWPOWER);
					return;
				} else {
					delayTargetTicks = TREE_FARM_PLANT_DELAY_TICKS;
					updateMetadata(BlockLaserTreeFarm.ICON_PLANTINGPOWERED);
				}
				
				itemStack.stackSize--;
				if (itemStack.stackSize <= 0) {
					itemStack = null;
				}
				inventory.setInventorySlotContents(slotIndex, itemStack);
				
				// totalPlanted++;
				int age = Math.max(10, Math.round((4 + worldObj.rand.nextFloat()) * WarpDriveConfig.MINING_LASER_MINE_DELAY_TICKS));
				PacketHandler.sendBeamPacket(worldObj, laserOutput, new Vector3(soil.x, soil.y + 1, soil.z).translate(0.5D),
						0.2F, 0.7F, 0.4F, age, 0, 50);
				worldObj.playSoundEffect(xCoord + 0.5f, yCoord, zCoord + 0.5f, "warpdrive:lowlaser", 4F, 1F);
			}
		}
	}
	
	@Override
	protected void stop() {
		super.stop();
		currentState = STATE_IDLE;
		updateMetadata(BlockLaserTreeFarm.ICON_IDLE);
	}
	
	private static boolean isSoil(Block block) {
		return WarpDriveConfig.BLOCKS_SOILS.contains(block);
	}
	
	private static boolean isLog(Block block) {
		return WarpDriveConfig.BLOCKS_LOGS.contains(block);
	}
	
	private static boolean isLeaf(Block block) {
		return WarpDriveConfig.BLOCKS_LEAVES.contains(block);
	}
	
	private LinkedList<VectorI> scanSoils() {
		int xmin = xCoord - radiusX;
		int xmax = xCoord + radiusX;
		int ymin = yCoord + 0;
		int ymax = yCoord + 8;
		int zmin = zCoord - radiusZ;
		int zmax = zCoord + radiusZ;
		
		LinkedList<VectorI> soilPositions = new LinkedList<VectorI>();
		
		for(int y = ymin; y <= ymax; y++) {
			for(int x = xmin; x <= xmax; x++) {
				for(int z = zmin; z <= zmax; z++) {
					if (worldObj.isAirBlock(x, y + 1, z)) {
						Block block = worldObj.getBlock(x, y, z);
						if (isSoil(block)) {
							VectorI pos = new VectorI(x, y, z);
							if (WarpDriveConfig.LOGGING_COLLECTION) {
								WarpDrive.logger.info("Found soil at " + x + "," + y + "," + z);
							}
							soilPositions.add(pos);
						}
					}
				}
			}
		}
		if (WarpDriveConfig.LOGGING_COLLECTION) {
			WarpDrive.logger.info("Found " + soilPositions.size() + " soils");
		}
		return soilPositions;
	}
	
	private Collection<VectorI> scanTrees() {
		int xmin = xCoord - radiusX;
		int xmax = xCoord + radiusX;
		int ymin = yCoord + 1;
		int ymax = yCoord + 1 + (tapTrees ? 8 : 0);
		int zmin = zCoord - radiusZ;
		int zmax = zCoord + radiusZ;
		
		Collection<VectorI> logPositions = new HashSet<VectorI>();
		
		for(int y = ymin; y <= ymax; y++) {
			for(int x = xmin; x <= xmax; x++) {
				for(int z = zmin; z <= zmax; z++) {
					Block block = worldObj.getBlock(x, y, z);
					if (isLog(block)) {
						VectorI pos = new VectorI(x, y, z);
						if (!logPositions.contains(pos)) {
							if (WarpDriveConfig.LOGGING_COLLECTION) {
								WarpDrive.logger.info("Found tree base at " + x + "," + y + "," + z);
							}
							logPositions.add(pos);
						}
					}
				}
			}
		}
		if (logPositions.size() > 0) {
			HashSet<Block> whitelist = (HashSet<Block>) WarpDriveConfig.BLOCKS_LOGS.clone();
			if (breakLeaves) {
				// whitelist.addAll(WarpDriveConfig.BLOCKS_LEAVES);
			}
			logPositions = getConnectedBlocks(worldObj, logPositions, UP_DIRECTIONS, whitelist, TREE_FARM_MAX_LOG_DISTANCE + laserMediumCount * TREE_FARM_MAX_LOG_DISTANCE_PER_MEDIUM);
		}
		if (WarpDriveConfig.LOGGING_COLLECTION) {
			WarpDrive.logger.info("Found " + logPositions.size() + " valuables");
		}
		return logPositions;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
		tag.setInteger("radiusX", radiusX);
		tag.setInteger("radiusZ", radiusZ);
		tag.setBoolean("breakLeaves", breakLeaves);
		tag.setBoolean("tapTrees", tapTrees);
		tag.setInteger("currentState", currentState);
	}
	
	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		radiusX = tag.getInteger("radiusX");
		if (radiusX == 0) {
			radiusX = RADIUS_DEFAULT;
		}
		radiusX = clamp(WarpDriveConfig.TREE_FARM_MIN_RADIUS, WarpDriveConfig.TREE_FARM_MAX_RADIUS, radiusX);
		radiusZ = tag.getInteger("radiusZ");
		if (radiusZ == 0) {
			radiusZ = RADIUS_DEFAULT;
		}
		radiusZ = clamp(WarpDriveConfig.TREE_FARM_MIN_RADIUS, WarpDriveConfig.TREE_FARM_MAX_RADIUS, radiusZ);
		
		breakLeaves     = tag.getBoolean("breakLeaves");
		tapTrees        = tag.getBoolean("tapTrees");
		currentState    = tag.getInteger("currentState");
		if (currentState == STATE_HARVEST || currentState == STATE_TAP || currentState == STATE_PLANT) {
			bScanOnReload = true;
		}
	}
	
	// OpenComputer callback methods
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] start(Context context, Arguments arguments) {
		return start(argumentsOCtoCC(arguments));
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] stop(Context context, Arguments arguments) {
		stop();
		return null;
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] state(Context context, Arguments arguments) {
		return state(argumentsOCtoCC(arguments));
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] radius(Context context, Arguments arguments) {
		return radius(argumentsOCtoCC(arguments));
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] breakLeaves(Context context, Arguments arguments) {
		return breakLeaves(argumentsOCtoCC(arguments));
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] silktouch(Context context, Arguments arguments) {
		return silktouch(argumentsOCtoCC(arguments));
	}
	
	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] tapTrees(Context context, Arguments arguments) {
		return tapTrees(argumentsOCtoCC(arguments));
	}
	
	// Common OC/CC methods
	private Object[] start(Object[] arguments) {
		if (isFarming()) {
			return new Object[] { false, "Already started" };
		}
		
		totalHarvested = 0;
		delayTicks = 0;
		currentState = STATE_WARMUP;
		return new Boolean[] { true };
	}
	
	private Object[] state(Object[] arguments) {
		int energy = getEnergyStored();
		String status = getStatus();
		Integer retValuables, retValuablesIndex;
		if (isFarming()) {
			retValuables = valuables.size();
			retValuablesIndex = valuableIndex;
			
			return new Object[] { status, isFarming(), energy, totalHarvested, retValuablesIndex, retValuables };
		}
		return new Object[] { status, isFarming(), energy, totalHarvested, 0, 0 };
	}
	
	private Object[] radius(Object[] arguments) {
		try {
			if (arguments.length == 1) {
				radiusX = clamp(WarpDriveConfig.TREE_FARM_MIN_RADIUS, WarpDriveConfig.TREE_FARM_MAX_RADIUS, toInt(arguments[0]));
				radiusZ = radiusX;
			} else if (arguments.length == 2) {
				radiusX = clamp(WarpDriveConfig.TREE_FARM_MIN_RADIUS, WarpDriveConfig.TREE_FARM_MAX_RADIUS, toInt(arguments[0]));
				radiusZ = clamp(WarpDriveConfig.TREE_FARM_MIN_RADIUS, WarpDriveConfig.TREE_FARM_MAX_RADIUS, toInt(arguments[1]));
			}
		} catch(NumberFormatException exception) {
			radiusX = RADIUS_DEFAULT;
			radiusZ = RADIUS_DEFAULT;
		}
		return new Integer[] { radiusX , radiusZ };
	}
	
	private Object[] breakLeaves(Object[] arguments) {
		if (arguments.length == 1) {
			try {
				breakLeaves = toBool(arguments[0]);
			} catch (Exception exception) {
				return new Object[] { breakLeaves };
			}
		}
		return new Object[] { breakLeaves };
	}
	
	private Object[] silktouch(Object[] arguments) {
		if (arguments.length == 1) {
			try {
				enableSilktouch = toBool(arguments[0]);
			} catch (Exception exception) {
				return new Object[] { enableSilktouch };
			}
		}
		return new Object[] { enableSilktouch };
	}
	
	private Object[] tapTrees(Object[] arguments) {
		if (arguments.length == 1) {
			try {
				tapTrees = toBool(arguments[0]);
			} catch (Exception exception) {
				return new Object[] { tapTrees };
			}
		}
		return new Object[] { tapTrees };
	}
	
	// ComputerCraft IPeripheral methods implementation
	@Override
	@Optional.Method(modid = "ComputerCraft")
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) {
		String methodName = getMethodName(method);
		
		if (methodName.equals("start")) {
			return start(arguments);
			
		} else if (methodName.equals("stop")) {
			stop();
			return null;
			
		} else if (methodName.equals("state")) {
			return state(arguments);
			
		} else if (methodName.equals("radius")) {
			return radius(arguments);
			
		} else if (methodName.equals("breakLeaves")) {
			return breakLeaves(arguments);
			
		} else if (methodName.equals("silktouch")) {
			return silktouch(arguments);
			
		} else if (methodName.equals("tapTrees")) {
			return tapTrees(arguments);
		}
		
		return super.callMethod(computer, context, method, arguments);
	}
	
	public String getStatus() {
		int energy = getEnergyStored();
		String state = "IDLE (not farming)";
		if (currentState == STATE_IDLE) {
			state = "IDLE (not farming)";
		} else if (currentState == STATE_WARMUP) {
			state = "Warming up...";
		} else if (currentState == STATE_SCAN) {
			if (breakLeaves) {
				state = "Scanning all";
			} else {
				state = "Scanning logs";
			}
		} else if (currentState == STATE_HARVEST) {
			if (breakLeaves) {
				state = "Harvesting all";
			} else {
				state = "Harvesting logs";
			}
			if (enableSilktouch) {
				state = state + " with silktouch";
			}
		} else if (currentState == STATE_TAP) {
			if (breakLeaves) {
				state = "Tapping trees, harvesting all";
			} else {
				state = "Tapping trees, harvesting logs";
			}
			if (enableSilktouch) {
				state = state + " with silktouch";
			}
		} else if (currentState == STATE_PLANT) {
			state = "Planting trees";
		}
		if (energy <= 0) {
			state = state + " - Out of energy";
		} else if (((currentState == STATE_SCAN) || (currentState == STATE_HARVEST) || (currentState == STATE_TAP)) && !enoughPower) {
			state = state + " - Not enough power";
		}
		return state;
	}
}
