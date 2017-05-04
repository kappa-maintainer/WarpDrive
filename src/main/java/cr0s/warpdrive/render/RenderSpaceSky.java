package cr0s.warpdrive.render;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.config.CelestialObjectManager;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.CelestialObject;
import cr0s.warpdrive.data.CelestialObject.RenderData;

import java.awt.Color;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.IRenderHandler;

import net.minecraftforge.fml.client.FMLClientHandler;
import org.lwjgl.opengl.GL11;

public class RenderSpaceSky extends IRenderHandler {
	private static RenderSpaceSky INSTANCE = null;
	
	public static RenderSpaceSky getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new RenderSpaceSky();
		}
		return INSTANCE;
	}
	
	public static final int callListStars = GLAllocation.generateDisplayLists(3);
	public static final int callListUpperSkyBox = callListStars + 1;
	public static final int callListBottomSkyBox = callListStars + 2;
	
	{
		// pre-generate the starfield
		GL11.glPushMatrix();
		GL11.glNewList(callListStars, GL11.GL_COMPILE);
		renderStars();
		GL11.glEndList();
		GL11.glPopMatrix();
		
		// pre-generate skyboxes
		final Tessellator tessellator = Tessellator.getInstance();
		final VertexBuffer vertexBuffer = tessellator.getBuffer();
		
		GL11.glNewList(callListUpperSkyBox, GL11.GL_COMPILE);
		final int stepSize = 64;
		final int nbSteps = 256 / stepSize + 2;
		float y = 16F;
		for (int x = -stepSize * nbSteps; x <= stepSize * nbSteps; x += stepSize) {
			for (int z = -stepSize * nbSteps; z <= stepSize * nbSteps; z += stepSize) {
				vertexBuffer.begin(7, DefaultVertexFormats.POSITION);
				vertexBuffer.pos(x           , y, z           ).endVertex();
				vertexBuffer.pos(x + stepSize, y, z           ).endVertex();
				vertexBuffer.pos(x + stepSize, y, z + stepSize).endVertex();
				vertexBuffer.pos(x           , y, z + stepSize).endVertex();
				tessellator.draw();
			}
		}
		GL11.glEndList();
		
		GL11.glNewList(callListBottomSkyBox, GL11.GL_COMPILE);
		y = -16F;
		vertexBuffer.begin(7, DefaultVertexFormats.POSITION);
		for (int x = -stepSize * nbSteps; x <= stepSize * nbSteps; x += stepSize) {
			for (int z = -stepSize * nbSteps; z <= stepSize * nbSteps; z += stepSize) {
				vertexBuffer.pos(x + stepSize, y, z           ).endVertex();
				vertexBuffer.pos(x           , y, z           ).endVertex();
				vertexBuffer.pos(x           , y, z + stepSize).endVertex();
				vertexBuffer.pos(x + stepSize, y, z + stepSize).endVertex();
			}
		}
		tessellator.draw();
		GL11.glEndList();
	}
	
	@Override
	public void render(float partialTicks, WorldClient world, Minecraft mc) {
		final Vec3d playerCoordinates = mc.thePlayer.getPositionEyes(partialTicks);
		final boolean isSpace = world.provider == null
		                     || WarpDrive.starMap.isInSpace(world, (int) playerCoordinates.xCoord, (int) playerCoordinates.zCoord);
		
		final Tessellator tessellator = Tessellator.getInstance();
		final VertexBuffer vertexBuffer = tessellator.getBuffer();
		
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDepthMask(false);
		
		// draw upper skybox
		/*
		final Vec3d skyColor = getCustomSkyColor();
		float skyColorRed   = (float) skyColor.xCoord * (1 - world.getStarBrightness(partialTicks) * 2);
		float skyColorGreen = (float) skyColor.yCoord * (1 - world.getStarBrightness(partialTicks) * 2);
		float skyColorBlue  = (float) skyColor.zCoord * (1 - world.getStarBrightness(partialTicks) * 2);
		float var8;

		if (mc.gameSettings.anaglyph) {
			final float var6 = (skyColorRed * 30.0F + skyColorGreen * 59.0F + skyColorBlue * 11.0F) / 100.0F;
			final float var7 = (skyColorRed * 30.0F + skyColorGreen * 70.0F) / 100.0F;
			var8 = (skyColorRed * 30.0F + skyColorBlue * 70.0F) / 100.0F;
			skyColorRed = var6;
			skyColorGreen = var7;
			skyColorBlue = var8;
		}
		
		// GL11.glEnable(GL11.GL_FOG);
		GL11.glColor3f(0.0F, 0.0F, 0.0F);
		GL11.glCallList(callListUpperSkyBox);
		// GL11.glDisable(GL11.GL_FOG);
		/**/
		
		// compute global alpha
		final float alphaBase = 1.0F - world.getRainStrength(partialTicks);
		
		// draw star systems
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		float starBrightness = 0.2F;
		if (world.provider != null) {
			starBrightness = world.provider.getStarBrightness(partialTicks);
		}
		if (starBrightness > 0.0F) {
			if (isSpace) {
				GL11.glColor4f(1.0F, 1.0F, 0.9F, alphaBase * starBrightness);
			} else {
				GL11.glColor4f(0.5F, 0.6F, 0.4F, alphaBase * starBrightness);
			}
			GL11.glCallList(callListStars);
		}
		
		// enable texture with alpha blending
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		
		// Star
		/*
		{
			GL11.glPushMatrix();
			final double starScale = isSpace ? 30.0D : 40.0D;
			final double starRange = 150.0D;    // max 190
			GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
			GL11.glRotatef(0.3F * 360.0F, 1.0F, 0.0F, 0.0F);    // Vanilla is world.getCelestialAngle(partialTicks) * 360.0F
			
			GL11.glColor4f(1.0F, 1.0F, 1.0F, isSpace ? 1.0F : 0.3F);
			FMLClientHandler.instance().getClient().renderEngine.bindTexture(textureStar);
			
			vertexBuffer.begin(7, DefaultVertexFormats.POSITION_TEX);
			vertexBuffer.pos(-starScale, starRange, -starScale).tex(0.0D, 0.0D).endVertex();
			vertexBuffer.pos( starScale, starRange, -starScale).tex(1.0D, 0.0D).endVertex();
			vertexBuffer.pos( starScale, starRange,  starScale).tex(1.0D, 1.0D).endVertex();
			vertexBuffer.pos(-starScale, starRange,  starScale).tex(0.0D, 1.0D).endVertex();
			tessellator.draw();
			GL11.glPopMatrix();
		}
		/**/
		
		// CelestialObject
		/*
		{
			GL11.glPushMatrix();
			final double planetScale = 10.0D;
			final double planetRange = 140.0D;
			final float planetRotation = (float) (world.getSpawnPoint().getZ() - mc.thePlayer.posZ) * 0.1F;
			GL11.glScalef(0.6F, 0.6F, 0.6F);
			GL11.glRotatef(planetRotation, 1.0F, 0.0F, 0.0F);
			GL11.glRotatef(190F, 1.0F, 0.0F, 0.0F);
			
			GL11.glColor4f(1.0F, 0.0F, 1.0F, 1.0F);
			FMLClientHandler.instance().getClient().renderEngine.bindTexture(texturePlanet);
			
			// world.getMoonPhase();
			vertexBuffer.begin(7, DefaultVertexFormats.POSITION_TEX);
			vertexBuffer.pos(-planetScale, planetRange, -planetScale).tex(0, 1).endVertex();
			vertexBuffer.pos( planetScale, planetRange, -planetScale).tex(1, 1).endVertex();
			vertexBuffer.pos( planetScale, planetRange,  planetScale).tex(1, 0).endVertex();
			vertexBuffer.pos(-planetScale, planetRange,  planetScale).tex(0, 0).endVertex();
			tessellator.draw();
			GL11.glScalef(1.0F, 1.0F, 1.0F);
			GL11.glPopMatrix();
		}
		/**/
		
		// Planets
		for(CelestialObject celestialObject : CelestialObjectManager.celestialObjects) {
			renderCelestialObject(tessellator, celestialObject, isSpace, mc.thePlayer.getEntityWorld().provider.getDimension(), playerCoordinates);
		}
		
		// final double playerAltitude = mc.thePlayer.getPositionEyes(partialTicks).yCoord - world.getHorizon();
		
		// stratosphere box
		/*
		float var10;
		float var11;
		float var12;
		if (playerAltitude < 0.0D) {
			GL11.glPushMatrix();
			GL11.glTranslatef(0.0F, 12.0F, 0.0F);
			GL11.glCallList(callListBottomSkyBox);
			GL11.glPopMatrix();
			var10 = 1.0F;
			var11 = -((float) (playerAltitude + 65.0D));
			var12 = -var10;
			GlStateManager.color(255, 128, 0, 255);
			vertexBuffer.begin(7, DefaultVertexFormats.POSITION);
			vertexBuffer.pos(-var10, var11,  var10).endVertex();
			vertexBuffer.pos( var10, var11,  var10).endVertex();
			vertexBuffer.pos( var10, var12,  var10).endVertex();
			vertexBuffer.pos(-var10, var12,  var10).endVertex();
			vertexBuffer.pos(-var10, var12, -var10).endVertex();
			vertexBuffer.pos( var10, var12, -var10).endVertex();
			vertexBuffer.pos( var10, var11, -var10).endVertex();
			vertexBuffer.pos(-var10, var11, -var10).endVertex();
			vertexBuffer.pos( var10, var12, -var10).endVertex();
			vertexBuffer.pos( var10, var12,  var10).endVertex();
			vertexBuffer.pos( var10, var11,  var10).endVertex();
			vertexBuffer.pos( var10, var11, -var10).endVertex();
			vertexBuffer.pos(-var10, var11, -var10).endVertex();
			vertexBuffer.pos(-var10, var11,  var10).endVertex();
			vertexBuffer.pos(-var10, var12,  var10).endVertex();
			vertexBuffer.pos(-var10, var12, -var10).endVertex();
			vertexBuffer.pos(-var10, var12, -var10).endVertex();
			vertexBuffer.pos(-var10, var12,  var10).endVertex();
			vertexBuffer.pos( var10, var12,  var10).endVertex();
			vertexBuffer.pos( var10, var12, -var10).endVertex();
			tessellator.draw();
		}
		/**/
		/*
		// draw bottom skybox relative to horizon
		GL11.glPushMatrix();
		GL11.glColor3f(0.30F, 0.30F, 0.30F);
		FMLClientHandler.instance().getClient().renderEngine.bindTexture(texturePlanet);
		
		// GL11.glTranslatef(0.0F, (float)(16.0D - playerAltitude), 0.0F);
		GL11.glCallList(callListBottomSkyBox);
		GL11.glPopMatrix();
		/**/
		
		
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_FOG);
		
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDepthMask(true);
	}
	
	static final double PLANET_FAR = 1786.0D;
	static final double PLANET_APPROACHING = 512.0D;
	static final double PLANET_ORBIT = 128.0D;
	private static void renderCelestialObject(Tessellator tessellator, final CelestialObject celestialObject, final boolean isSpace, final int dimensionId, final Vec3d vec3Player) {
		// @TODO compute relative coordinates for rendering on celestialObject
		if (dimensionId != celestialObject.parentDimensionId) {
			return;
		}
		
		final double distanceToCenterX = celestialObject.parentCenterX - vec3Player.xCoord;
		final double distanceToCenterZ = celestialObject.parentCenterZ - vec3Player.zCoord;
		final double distanceToBorder = Math.sqrt(Math.max(0, celestialObject.getSquareDistanceInParent(dimensionId, vec3Player.xCoord, vec3Player.zCoord)));
		final double distanceToCenter = Math.sqrt(distanceToCenterX * distanceToCenterX + distanceToCenterZ * distanceToCenterZ);
		
		// transition values
		// distance              far   approaching  orbit
		// world border         1.000     1.000     1.000
		// PLANET_FAR           1.000     1.000     1.000
		// PLANET_APPROACHING   0.000     1.000     1.000
		// PLANET_ORBIT         0.000     0.000     1.000
		// in orbit             0.000     0.000     0.000
		final double transitionFar         = (Math.max(PLANET_APPROACHING, Math.min(PLANET_FAR, distanceToBorder)) - PLANET_APPROACHING) / (PLANET_FAR - PLANET_APPROACHING);
		final double transitionApproaching = (Math.max(PLANET_ORBIT, Math.min(PLANET_APPROACHING, distanceToBorder)) - PLANET_ORBIT) / (PLANET_APPROACHING - PLANET_ORBIT);
		final double transitionOrbit       = Math.max(0.0D, Math.min(PLANET_ORBIT, distanceToBorder)) / PLANET_ORBIT;
		
		// relative position above celestialObject
		final double offsetX = (1.0 - transitionOrbit) * (distanceToCenterX / celestialObject.borderRadiusX);
		final double offsetZ = (1.0 - transitionOrbit) * (distanceToCenterZ / celestialObject.borderRadiusZ);
		
		// simulating a non-planar universe...
		final double planetY_far = (celestialObject.dimensionId + 99 % 100 - 50) * Math.log(distanceToCenter) / 4.0D;
		final double planetY = planetY_far * transitionApproaching;
		
		// render range is only used for Z-ordering
		double renderRange = 180.0D + 10.0D * (distanceToCenter / Math.max(celestialObject.borderRadiusX, celestialObject.borderRadiusZ));
		
		// render size is 1 at space border range
		// render size is 10 at approaching range
		// render size is 90 at orbit range
		// render size is min(1000, celestialObject border) at orbit range
		final double renderSize = 100.0D / 1000.0D * Math.min(1000.0D, Math.max(celestialObject.borderRadiusX, celestialObject.borderRadiusZ)) * (1.0D - transitionOrbit)
								+ 50.0D * (transitionOrbit < 1.0D ? transitionOrbit : (1.0D - transitionApproaching))
								+ 5.0D * (transitionApproaching < 1.0D ? transitionApproaching : (1.0D - transitionFar))
								+ 2.0D * transitionFar;
		
		// angles
		@SuppressWarnings("SuspiciousNameCombination")
		final double angleH = Math.atan2(distanceToCenterX, distanceToCenterZ);
		@SuppressWarnings("SuspiciousNameCombination")
		final double angleV_far = Math.atan2(Math.sqrt(distanceToCenterX * distanceToCenterX + distanceToCenterZ * distanceToCenterZ), planetY);
		final double angleV = Math.PI * (1.0D - transitionOrbit) + angleV_far * transitionOrbit;
		final double angleS = 0.15D * celestialObject.dimensionId * transitionApproaching // + (world.getTotalWorldTime() + partialTicks) * Math.PI / 6000.0D;
							+ angleH * (1.0D - transitionApproaching);
		
		if ( WarpDriveConfig.LOGGING_RENDERING
		  && celestialObject.dimensionId == 1
		  && (Minecraft.getSystemTime() / 10) % 100 == 0) {
			WarpDrive.logger.info(String.format("transition Far %.2f Approaching %.2f Orbit %.2f distanceToCenter %.3f %.3f offset %.3f %.3f angle H %.3f V_far %.3f V %.3f S %.3f",
				transitionFar, transitionApproaching, transitionOrbit, distanceToCenterX, distanceToCenterZ, offsetX, offsetZ, angleH, angleV_far, angleV, angleS));
		}
		
		// pre-computations
		final double sinH = Math.sin(angleH);
		final double cosH = Math.cos(angleH);
		final double sinV = Math.sin(angleV);
		final double cosV = Math.cos(angleV);
		final double sinS = Math.sin(angleS);
		final double cosS = Math.cos(angleS);
		
		GL11.glPushMatrix();
		
		// GL11.glEnable(GL11.GL_BLEND);    // by caller
		final double time = Minecraft.getSystemTime() / 1000.0D;
		final VertexBuffer vertexBuffer = tessellator.getBuffer();
		for (RenderData renderData : celestialObject.setRenderData) {
			// compute texture offsets for clouds animation 
			final float offsetU = (float) ( Math.signum(renderData.periodU) * ((time / Math.abs(renderData.periodU)) % 1.0D) );
			final float offsetV = (float) ( Math.signum(renderData.periodV) * ((time / Math.abs(renderData.periodV)) % 1.0D) );
			
			// apply rendering parameters
			GL11.glColor4f(renderData.red, renderData.green, renderData.blue, renderData.alpha * (isSpace ? 1.0F : 0.2F));
			if (renderData.texture != null) {
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				FMLClientHandler.instance().getClient().renderEngine.bindTexture(renderData.resourceLocation);
				vertexBuffer.begin(7, DefaultVertexFormats.POSITION_TEX);
			} else {
				GL11.glDisable(GL11.GL_TEXTURE_2D);
				vertexBuffer.begin(7, DefaultVertexFormats.POSITION);
			}
			if (renderData.isAdditive) {
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			} else {
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			}
			
			// draw current layer
			for (int indexVertex = 0; indexVertex < 4; indexVertex++) {
				final double offset1 = ((indexVertex & 2) - 1) * renderSize;
				final double offset2 = ((indexVertex + 1 & 2) - 1) * renderSize;
				final double valV = offset1 * cosS - offset2 * sinS;
				final double valH = offset2 * cosS + offset1 * sinS;
				final double y = valV * sinV + renderRange * cosV;
				final double valD = renderRange * sinV - valV * cosV;
				final double x = valD * sinH - valH * cosH + renderSize * offsetX;
				final double z = valH * sinH + valD * cosH + renderSize * offsetZ;
				if (renderData.texture != null) {
					vertexBuffer.pos(x, y, z).tex((indexVertex & 2) / 2 + offsetU, (indexVertex + 1 & 2) / 2 + offsetV).endVertex();
				} else {
					vertexBuffer.pos(x, y, z).endVertex();
				}
			}
			tessellator.draw();
			
			// slight offset to get volumetric illusion
			renderRange -= 5.0D;
		}
		
		// restore settings
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		
		GL11.glPopMatrix();
	}
	
	private void renderStars() {
		final Random rand = new Random(10842L);
		final boolean hasMoreStars = rand.nextBoolean() || rand.nextBoolean();
		final Tessellator tessellator = Tessellator.getInstance();
		final VertexBuffer vertexBuffer = tessellator.getBuffer();
		
		final double renderRangeMax = 200.0D;
		for (int indexStars = 0; indexStars < (hasMoreStars ? 20000 : 2000); indexStars++) {
			double randomX;
			double randomY;
			double randomZ;
			double randomLength;
			do {
				randomX = rand.nextDouble() * 2.0D - 1.0D;
				randomY = rand.nextDouble() * 2.0D - 1.0D;
				randomZ = rand.nextDouble() * 2.0D - 1.0D;
				randomLength = randomX * randomX + randomY * randomY + randomZ * randomZ;
			} while (randomLength >= 1.0D || randomLength <= 0.90D);
			
			final double renderSize = 0.4F + 0.05F * Math.log(1.1D - rand.nextDouble());
			
			// forcing Z-order
			randomLength = 1.0D / Math.sqrt(randomLength);
			randomX *= randomLength;
			randomY *= randomLength;
			randomZ *= randomLength;
			
			// scaling
			final double x0 = randomX * renderRangeMax;
			final double y0 = randomY * renderRangeMax;
			final double z0 = randomZ * renderRangeMax;
			
			// angles
			@SuppressWarnings("SuspiciousNameCombination")
			final double angleH = Math.atan2(randomX, randomZ);
			@SuppressWarnings("SuspiciousNameCombination")
			final double angleV = Math.atan2(Math.sqrt(randomX * randomX + randomZ * randomZ), randomY);
			final double angleS = rand.nextDouble() * Math.PI * 2.0D;
			
			// colorization
			final int rgb = getStarColorRGB(rand);
			GL11.glColor4f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F /* isSpace ? 1.0F : 0.2F /**/);
			
			// pre-computations
			final double sinH = Math.sin(angleH);
			final double cosH = Math.cos(angleH);
			final double sinV = Math.sin(angleV);
			final double cosV = Math.cos(angleV);
			final double sinS = Math.sin(angleS);
			final double cosS = Math.cos(angleS);
			
			vertexBuffer.begin(7, DefaultVertexFormats.POSITION);
			for (int indexVertex = 0; indexVertex < 4; indexVertex++) {
				final double valZero = 0.0D;
				final double offset1 = ((indexVertex     & 2) - 1) * renderSize;
				final double offset2 = ((indexVertex + 1 & 2) - 1) * renderSize;
				final double valV = offset1 * cosS - offset2 * sinS;
				final double valH = offset2 * cosS + offset1 * sinS;
				final double y1 = valV * sinV + valZero * cosV;
				final double valD = valZero * sinV - valV * cosV;
				final double x1 = valD * sinH - valH * cosH;
				final double z1 = valH * sinH + valD * cosH;
				vertexBuffer.pos(x0 + x1, y0 + y1, z0 + z1).endVertex();
			}
			tessellator.draw();
		}
		
	}
	
	// colorization loosely inspired from Hertzsprung-Russell diagram
	// (we're using it for non-star objects too, so yeah...)
	private static int getStarColorRGB(Random rand) {
		final double colorType = rand.nextDouble();
		float hue;
		float saturation;
		float brightness = 1.0F - 0.8F * rand.nextFloat();  // distance effect
		
		if (colorType <= 0.08D) {// 8% light blue (young star)
			hue = 0.48F + 0.08F * rand.nextFloat();
			saturation = 0.18F + 0.22F * rand.nextFloat();
			
		} else if (colorType <= 0.24D) {// 22% pure white (early age)
			hue = 0.126F + 0.040F * rand.nextFloat();
			saturation = 0.00F + 0.15F * rand.nextFloat();
			brightness *= 0.95F;
			
		} else if (colorType <= 0.45D) {// 21% yellow white
			hue = 0.126F + 0.040F * rand.nextFloat();
			saturation = 0.15F + 0.15F * rand.nextFloat();
			brightness *= 0.90F;
			
		} else if (colorType <= 0.67D) {// 22% yellow
			hue = 0.126F + 0.040F * rand.nextFloat();
			saturation = 0.80F + 0.15F * rand.nextFloat();
			if (rand.nextInt(3) == 1) {// yellow giant
				brightness *= 0.90F;
			} else {
				brightness *= 0.85F;
			}
			
		} else if (colorType <= 0.92D) {// 25% orange
			hue = 0.055F + 0.055F * rand.nextFloat();
			saturation = 0.85F + 0.15F * rand.nextFloat();
			if (rand.nextInt(3) == 1) {// (orange giant)
				brightness *= 0.90F;
			} else {
				brightness *= 0.80F;
			}
			
		} else {// red (mostly giants)
			hue = 0.95F + 0.05F * rand.nextFloat();
			if (rand.nextInt(3) == 1) {// (red giant)
				saturation = 0.80F + 0.20F * rand.nextFloat();
				brightness *= 0.95F;
			} else {
				saturation = 0.70F + 0.20F * rand.nextFloat();
				brightness *= 0.65F;
			}
		}
		return Color.HSBtoRGB(hue, saturation, brightness);
	}
	
	private static Vec3d getCustomSkyColor() {
		return new Vec3d(0.26796875D, 0.1796875D, 0.0D);
	}
	
	public static float getSkyBrightness(float par1) {
		final float var2 = FMLClientHandler.instance().getClient().theWorld.getCelestialAngle(par1);
		float var3 = 1.0F - (MathHelper.sin(var2 * (float) Math.PI * 2.0F) * 2.0F + 0.25F);

		if (var3 < 0.0F) {
			var3 = 0.0F;
		}

		if (var3 > 1.0F) {
			var3 = 1.0F;
		}

		return var3 * var3 * 1F;
	}
}
