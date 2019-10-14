package cr0s.warpdrive.world;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.google.common.base.Predicate;

public class FakeChunk extends Chunk {
	
	final private World world;
	
	public FakeChunk(final World world, final int x, final int z) {
		super(world, x, z);
		
		this.world = world;
	}
	
	@Override
	public boolean isAtLocation(final int x, final int z) {
		return x == this.x && z == this.z;
	}
	
	@Override
	public int getHeightValue(final int x, final int z) {
		return 1;
	}
	
	@Override
	public void generateHeightMap() {
	}
	
	@Override
	public void generateSkylightMap() {
	}
	
	@Nonnull
	@Override
	public IBlockState getBlockState(final BlockPos blockPos) {
		return world.getBlockState(blockPos);
	}
	
	@Override
	public int getBlockLightOpacity(@Nonnull final BlockPos blockPos) {
		return 255;
	}
	
	@Override
	public int getLightFor(final EnumSkyBlock enumSkyBlock, final BlockPos blockPos) {
		return enumSkyBlock.defaultLightValue;
	}
	
	@Override
	public void setLightFor(final EnumSkyBlock enumSkyBlock, final BlockPos blockPos, final int value) {
	}
	
	@Override
	public int getLightSubtracted(final BlockPos blockPos, final int amount) {
		return 0;
	}
	
	@Override
	public void addEntity(final Entity entity) {
	}
	
	@Override
	public void removeEntity(final Entity entity) {
	}
	
	@Override
	public void removeEntityAtIndex(@Nonnull final Entity entity, final int index) {
	}
	
	@Override
	public boolean canSeeSky(final BlockPos blockPos)	{
		return false;
	}
	
	@Nullable
	@Override
	public TileEntity getTileEntity(@Nonnull final BlockPos blockPos, final Chunk.EnumCreateEntityType creationMode) {
		return world.getTileEntity(blockPos);
	}
	
	@Override
	public void addTileEntity(final TileEntity tileEntity) {
	}
	
	@Override
	public void addTileEntity(@Nonnull final BlockPos blockPos, final TileEntity tileEntity) {
	}
	
	@Override
	public void removeTileEntity(@Nonnull final BlockPos blockPos) {
		
	}
	
	@Override
	public void onLoad() {
	}
	
	@Override
	public void onUnload() {
	}
	
	@Override
	public void markDirty() {
	}
	
	@Override
	public void getEntitiesWithinAABBForEntity(@Nullable final Entity entityIn, final AxisAlignedBB aabb,
	                                           @Nonnull final List<Entity> listToFill, final Predicate<? super Entity> filter) {
	}
	
	@Override
	public <T extends Entity> void getEntitiesOfTypeWithinAABB(@Nonnull final Class<? extends T> entityClass, final AxisAlignedBB aabb,
	                                                           @Nonnull final List<T> listToFill, final Predicate<? super T> filter) {
	}
	
	@Override
	public boolean needsSaving(final boolean p_76601_1_) {
		return false;
	}
	
	@Override
	public boolean isEmpty() {
		return false;
	}
	
	@Override
	public boolean isEmptyBetween(final int startY, final int endY) {
		return false;
	}
}