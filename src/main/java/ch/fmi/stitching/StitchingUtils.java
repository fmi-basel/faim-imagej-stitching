/*-
 * #%L
 * ImageJ utilities and commands for stitching various datasets
 * %%
 * Copyright (C) 2019 - 2023 Friedrich Miescher Institute for Biomedical
 * 			Research, Basel
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package ch.fmi.stitching;

import ij.ImagePlus;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import mpicbg.models.InvertibleBoundable;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.stitching.CollectionStitchingImgLib;
import mpicbg.stitching.ImageCollectionElement;
import mpicbg.stitching.ImagePlusTimePoint;
import mpicbg.stitching.StitchingParameters;
import mpicbg.stitching.fusion.Fusion;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * 
 * @author Jan Eglinger
 *
 */
public class StitchingUtils {

	public static final int BLENDING_FUSION = 0;
	public static final int AVERAGE_FUSION = 1;
	public static final int MEDIAN_FUSION = 2;
	public static final int MAX_FUSION = 3;
	public static final int MIN_FUSION = 4;
	public static final int OVERLAP_FUSION = 5;

	private StitchingUtils() {
		// prevent instantiation of static utility class
	}

	/**
	 * Create {@link StitchingParameters} with sensible defaults.
	 * 
	 * The {@code cpMemChoice}, {@code dimensionality} and {@code computeOverlap} fields
	 * of the returned object still need to be set by the consumer.
	 * 
 	 * @return Default {@link StitchingParameters} to be used for stitching
	 */
	public static StitchingParameters defaultParameters() {
		StitchingParameters params = new StitchingParameters();
		params.checkPeaks = 5;
		params.subpixelAccuracy = true;
		params.regThreshold = 0.7;
		return params;
	}

	/**
	 * Compute optimal tile positions from a list of known initial positions
	 * 
	 * @param images List of tiles
	 * @param positions List of known positions
	 * @param dimensionality 2 or 3
	 * @param computeOverlap if true, compute the exact tile overlap; if false, trust the known coordinates
	 * @return List of transformation models
	 */
	public static ArrayList<InvertibleBoundable> computeStitching(ArrayList<ImagePlus> images, List<float[]> positions, int dimensionality, boolean computeOverlap) {
		return computeStitching(images, positions, dimensionality, computeOverlap, false);
	}

	/**
	 * Compute optimal tile positions from a list of known initial positions
	 * 
	 * @param images List of tiles
	 * @param positions List of known positions
	 * @param dimensionality 2 or 3
	 * @param computeOverlap If true, compute the exact tile overlap; if false, trust the known coordinates
	 * @param saveMemory If true, save memory at the cost of computation time; if false, use more RAM 
	 * @return List of transformation models
	 */
	public static ArrayList<InvertibleBoundable> computeStitching(ArrayList<ImagePlus> images, List<float[]> positions, int dimensionality, boolean computeOverlap, boolean saveMemory) {
		// Create parameters
		StitchingParameters params = defaultParameters();
		params.cpuMemChoice = saveMemory ? 0 : 1; // 1 = faster, use more RAM
		params.dimensionality = dimensionality;
		params.computeOverlap = computeOverlap;

		return computeStitching(images, positions, params);
	}

	/**
	 * Compute optimal tile positions from a list of known initial positions
	 * 
	 * @param images List of tiles
	 * @param positions List of known positions
	 * @param params {@link StitchingParameters} defining the options for stitching
	 * @return List of transformation models
	 */
	public static ArrayList<InvertibleBoundable> computeStitching(ArrayList<ImagePlus> images, List<float[]> positions, StitchingParameters params) {
		// TODO consider changing signature to List instead of ArrayList
		// (although Fusion.fuse requires ArrayList anyways...)
		if (images.size() != positions.size()) {
			throw new RuntimeException("number of images (" + images.size() + ") != number of positions (" + positions.size() + ")");
		}

		// Create tile list
		ArrayList<ImageCollectionElement> elements = new ArrayList<>();
		float[] pos;
		for (int i = 0; i < images.size(); i++) {
			ImageCollectionElement element = new ImageCollectionElement(null, i);
			element.setDimensionality( params.dimensionality );
			element.setModel(params.dimensionality == 2 ? new TranslationModel2D() : new TranslationModel3D());
			element.setImagePlus(images.get(i));
			if (params.dimensionality == 2) {
				element.setOffset(positions.get(i));
			} else {
				pos = positions.get(i);
				element.setOffset(new float[] {pos[0], pos[1], 0});
			}
			elements.add(element);
		}

		// Compute stitching
		ArrayList<ImagePlusTimePoint> tiles = CollectionStitchingImgLib.stitchCollection(elements, params);

		// Extract models
		ArrayList<InvertibleBoundable> models = new ArrayList<>();
		for (ImagePlusTimePoint tile : tiles) {
			models.add((InvertibleBoundable) tile.getModel());
		}
		return models;
	}

	/**
	 * Fuse a set of tiles, given a set of transformation models and a fusion type
	 * 
	 * @param images List of tiles
	 * @param models List of transformation models
	 * @param dimensionality 2 or 3
	 * @param fusionType Type of fusion, one of the following:
	 * <ul>
	 *   <li>{@code BLENDING_FUSION}</li>
	 *   <li>{@code AVERAGE_FUSION}</li>
	 *   <li>{@code MEDIAN_FUSION}</li>
	 *   <li>{@code MAX_FUSION}</li>
	 *   <li>{@code MIN_FUSION}</li>
	 *   <li>{@code OVERLAP_FUSION}</li>
	 * </ul>
	 * @return fused image
	 */
	public static ImagePlus fuseTiles(ArrayList<ImagePlus> images, ArrayList<InvertibleBoundable> models, int dimensionality, int fusionType) {
		switch (images.get(0).getType()) {
			case ImagePlus.GRAY8:
				return Fusion.fuse(new UnsignedByteType(), images, models, dimensionality, true, fusionType, null, false, false, false);
			case ImagePlus.GRAY16:
				return Fusion.fuse(new UnsignedShortType(), images, models, dimensionality, true, fusionType, null, false, false, false);
			case ImagePlus.GRAY32:
				return Fusion.fuse(new FloatType(), images, models, dimensionality, true, fusionType, null, false, false, false);
			default:
				throw new RuntimeException("Unknown image type for fusion");
		}
	}

	/**
	 * Fuse a set of tiles, given a set of transformation models
	 * 
	 * @param images List of tiles
	 * @param models List of transformation models
	 * @param dimensionality 2 or 3
	 * @return fused image
	 */
	public static ImagePlus fuseTiles(ArrayList<ImagePlus> images, ArrayList<InvertibleBoundable> models, int dimensionality) {
		return fuseTiles(images, models, dimensionality, BLENDING_FUSION);
	}

	/**
	 * Create a preview image of a tile layout
	 * 
	 * @param image {@code BufferedImage} to draw into
	 * @param pixelPositions List of positions (2D pixel coordinates)
	 * @param xSize Width of a single tile
	 * @param ySize Height of a single tile
	 */
	public static void drawPositions(BufferedImage image, List<float[]> pixelPositions, long xSize, long ySize)
	{
		if (image == null) return;

		Float xMin = Float.POSITIVE_INFINITY;
		Float yMin = Float.POSITIVE_INFINITY;
		Float xMax = Float.NEGATIVE_INFINITY;
		Float yMax = Float.NEGATIVE_INFINITY;

		for (float[] p : pixelPositions) {
			if (p[0] < xMin) xMin = p[0];
			if (p[1] < yMin) yMin = p[1];
			if (p[0] > xMax) xMax = p[0];
			if (p[1] > yMax) yMax = p[1];
		}

		long tileWidth = xSize;
		long tileHeight = ySize;

		xMax += tileWidth;
		yMax += tileHeight;

		int width = image.getWidth();
		int height = image.getHeight();
		double scaledWidth = xMax - xMin;
		double scaledHeight = yMax - yMin;
		Float offsetX = xMin;
		Float offsetY = yMin;
		double factor = Math.max(scaledWidth, scaledHeight) / Math.min(width,
			height);

		Graphics g = image.getGraphics();
		g.clearRect(0, 0, width, height);
		int rgb = 0;
		for (float[] p : pixelPositions) {
			rgb = nextColor(rgb);
			g.setXORMode(new Color(rgb)); // TODO replace by HSB color directly?
			g.fillRect((int) ((p[0] - offsetX) / factor), (int) ((p[1] - offsetY) /
				factor), (int) (tileWidth / factor), (int) (tileHeight / factor));
		}
		g.dispose();
	}

	private static int nextColor(int rgb) {
		// Create a "golden angle" color sequence for best contrast, see:
		// https://github.com/ijpb/MorphoLibJ/blob/c6c688f/src/main/java/inra/ijpb/util/ColorMaps.java#L315

		float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb &
			0xFF, null);
		hsb[0] += 0.38197;
		if (hsb[0] > 1) hsb[0] -= 1;
		hsb[1] += 0.38197;
		if (hsb[1] > 1) hsb[1] -= 1;
		hsb[1] = 0.5f * hsb[1] + 0.5f;
		return Color.HSBtoRGB(hsb[0], hsb[1], 1);
	}
}
