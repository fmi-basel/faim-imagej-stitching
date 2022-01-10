/*-
 * #%L
 * ImageJ utilities and commands for stitching various datasets
 * %%
 * Copyright (C) 2019 - 2022 Friedrich Miescher Institute for Biomedical
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

package ch.fmi.stitching.visiview;

import ch.fmi.stitching.StitchingUtils;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.ZProjector;
import io.scif.SCIFIO;
import io.scif.services.FormatService;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import mpicbg.models.InvertibleBoundable;
import net.imagej.ImgPlus;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.algorithm.localization.*;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.jetbrains.annotations.Nullable;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ch.fmi.stitching.visiview.UIConstants.*;

@Plugin(type = Command.class, headless = true,
	menuPath = "FMI>VisiView Data>Stitch Dataset with DAPI Illumination Correction",
	initializer = "initializeDialog")
public class StitchVisiviewIlluminationCorrectedDatasetCommand extends
	DynamicCommand
{

	@Parameter(label = "Input dataset file (nd)", style = "extensions:nd",
		callback = "ndFileChanged", persist = false)
	private File ndFile;

	@Parameter(label = " ", visibility = ItemVisibility.MESSAGE, persist = false,
		required = false)
	private String ndMessage = " ";

	@Parameter(label = "Stage position file (stg)", style = "extensions:stg",
		callback = "stgFileChanged", required = false, persist = false)
	private File stgFile = null;

	@Parameter(label = " ", visibility = ItemVisibility.MESSAGE, persist = false,
		required = false)
	private String stgMessage = " ";

	@Parameter(label = "Overlap computation mode", style = "radioButtonVertical", //
		choices = { COMPUTE_NONE, COMPUTE_VIA_MIP }, required = false)
	private String stitchingMode = COMPUTE_NONE;

	@Parameter(label = "Output", style = "radioButtonVertical", //
		choices = { OUTPUT_TXT, OUTPUT_MIP }, required = false)
	private String outputMode = OUTPUT_MIP;

	@Parameter(label = "Save RAM at the cost of speed", required = false)
	private boolean saveRAM = false;

	@Parameter(label = "Override calibration metadata with provided values",
		required = false)
	private Boolean doOverrideCalibration = false;

	@Parameter(label = "DAPI channel")
	private Integer dapiChannelIndex = 3;

	@Parameter(label = "Pixel spacing (x)", callback = "xSpacingChanged")
	private Double xCal;

	@Parameter(label = "Pixel spacing (y)", callback = "ySpacingChanged")
	private Double yCal;

	@Parameter(label = "Pixel spacing (z)")
	private Double zCal;

	@Parameter(required = false, persist = false)
	private BufferedImage layout;

	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus fused;

	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus estimatedIlluminationField;

	@Parameter
	private SCIFIO scifio;

	@Parameter
	private FormatService formatService;

	@Parameter
	private LogService logService;

	private boolean is2D;
	private long xSize;
	private long ySize;
	private long zSize;
	private long nChannels;
	private long nTimepoints;
	private int nSeries;

	private List<float[]> pixelPositions; // holds the pixel-based positions
	private List<String> positionNames;
	private ArrayList<ImagePlus> images; // ArrayList required by stitching API
	private IntervalView<FloatType> illumination_img;
	private ArrayList<InvertibleBoundable> models;

	private boolean stgRequired;

	private boolean ndFileChanged = false;
	private boolean stgFileChanged = false;

	private boolean validDatasetInfo = false;

	@Override
	public void run() {
		logService.debug("Now running...");

		// Ensure valid input parameters
		if (!doOverrideCalibration || !validDatasetInfo) updateNdFileInfo();
		if (stgRequired && stgFile != null && stgFile.exists()) {
			updateStgFileInfo();
		}
		else if (stgRequired) {
			autoupdateStgFileParameter();
		}

		// Start stitching process
		if (pixelPositions.size() > 0) {
			logService.error("Now stitching with defined positions");

			is2D = (zSize > 1) ? false : true;

			if (nSeries == 1) {
				// get single stack, split into ImageCollectionElements, stitch online
				// parameters: imp(Stack), positions
				try {
					ImagePlus[] imps = BF.openImagePlus(ndFile.getAbsolutePath());

					Duplicator d = new Duplicator();

					images = new ArrayList<>();
					for (int i = 0; i < imps[0].getNSlices(); i++) {
						images.add(d.run(imps[0], 1, imps[0].getNChannels(), i + 1, i + 1,
							1, imps[0].getNFrames()));
					}

				}
				catch (FormatException exc) {
					logService.error("Error performing a file format operation", exc);
					return;
				}
				catch (IOException exc) {
					logService.error("Error reading file", exc);
					return;
				}

				models = StitchingUtils.computeStitching(images, pixelPositions, 2,
					!stitchingMode.equals(COMPUTE_NONE), saveRAM);

				fused = StitchingUtils.fuseTiles(images, models, 2);

				// fused.setTitle(imps[0].getTitle() + "_fused");
				fused.setTitle("Fused");
				Calibration cal = new Calibration();
				cal.pixelWidth = xCal;
				cal.pixelHeight = yCal;
				cal.pixelDepth = zCal;
				fused.setCalibration(cal);

				// TODO close all images?
			}
			else if (stitchingMode.equals(COMPUTE_VIA_MIP) || outputMode.equals(
				OUTPUT_MIP))
			{
				// create MIPs for all series, stitch online
				// parameters imp[], positions
				logService.info("Loading tiles and computing MIPs...");

				// read each series, create MIP, add to list
				try {
					images = new ArrayList<>();

					ImporterOptions options = new ImporterOptions();
					options.setOpenAllSeries(false);
					options.setId(ndFile.getAbsolutePath());
					options.setSeriesOn(0, true);

					for (int i = 0; i < nSeries; i++) {
						ImagePlus img = BF.openImagePlus(options)[0];
						if (i == 0) {
							illumination_img = Views.hyperSlice(ImagePlusAdapter.convertFloat(
								img), 2, dapiChannelIndex);
						}
						else {
							LoopBuilder.setImages(illumination_img, Views.hyperSlice(
								ImagePlusAdapter.convertFloat(img), 2, dapiChannelIndex))
								.forEachPixel((s, t) -> s.setReal(s.getRealFloat() + t
									.getRealFloat()));
						}
						images.add(createMIP(img, ZProjector.MAX_METHOD));
						options.setSeriesOn(i, false);
						options.setSeriesOn(i + 1, true);
					}
				}
				catch (IOException exc) {
					logService.error("Error reading file", exc);
					return;
				}
				catch (FormatException exc) {
					logService.error("Error performing a file format operation", exc);
					return;
				}

				try {
					final float n_tiles = (float) images.size();
					LoopBuilder.setImages(illumination_img).forEachPixel(s -> s.setReal(s
						.getRealFloat() / n_tiles));
					final Img<FloatType> fitted_illumination_field = getIlluminationField(
						illumination_img, dapiChannelIndex);
					estimatedIlluminationField = ImageJFunctions.wrap(
						fitted_illumination_field, "Estimated Illumination Field");

					logService.info("Correcting illumination...");
					final FloatType min = new FloatType();
					final FloatType max = new FloatType();
					ComputeMinMax.computeMinMax(fitted_illumination_field, min, max);
					for (ImagePlus mip : images) {
						IntervalView<RealType> dapi = (IntervalView<RealType>) Views
							.hyperSlice((ImgPlus<?>) ImagePlusAdapter.wrapImgPlus(mip), 2,
								dapiChannelIndex);
						LoopBuilder.setImages(dapi, fitted_illumination_field).forEachPixel(
							(dapi_pixel, illumination_field_pixel) -> {
								dapi_pixel.setReal(dapi_pixel.getRealFloat() /
									illumination_field_pixel.getRealFloat() * max
										.getRealDouble());
							});

					}
				}
				catch (Exception exc) {
					logService.error("Could not fit Gaussian.", exc);
					return;
				}

				logService.info("Stitching MIPs...");
				models = StitchingUtils.computeStitching(images, pixelPositions, 2,
					stitchingMode.equals(COMPUTE_NONE) ? false : true, saveRAM);

				// case: via MIP: go on with full dataset
				// load all full series into imps[]
				// case: MIP output: fuse

				fused = StitchingUtils.fuseTiles(images, models, 2);

				// TODO set title and calibration

			}
			else { // stitch with TileConfiguration.txt file
				// String tileConfigPath = writeTileConfiguration(ndFile,
				// pixelPositions, is2D);

				// stitchClassical(tileConfigPath);
				try {
					// load all series into imps[]
					ImporterOptions options = new ImporterOptions();
					options.setOpenAllSeries(true);
					options.setId(ndFile.getAbsolutePath());
					ImagePlus[] imps = BF.openImagePlus(options);

					images = new ArrayList<>();
					images.addAll(Arrays.asList(imps));
					// computeStitching
					models = StitchingUtils.computeStitching(images, pixelPositions, is2D
						? 2 : 3, stitchingMode.equals(COMPUTE_NONE) ? false : true,
						saveRAM);
					// fuseTiles
					fused = StitchingUtils.fuseTiles(images, models, is2D ? 2 : 3);
				}
				catch (FormatException exc) {
					logService.error("Error performing a file format operation", exc);
					return;
				}
				catch (IOException exc) {
					logService.error("Error reading file", exc);
					return;
				}
			}
			// TODO consolidate StitchingUtils calls to here (using is2D)
		}
		else {
			logService.error("Initial tile positions cannot be determined.");
			// TODO offer possibility of stitching unknown positions ?
			return;
		}

		// cases:
		// 1.a) stg == null => get rows/cols from nd file
		// 1.b) nPositions > 0 => get position list

		// 2.a) zSize == 1 && nTimepoints == 1 => 2D stack stitching (imp[])
		// 2.b) MIP ? => create MIPs, the 2D stitching (imp[])

		// 1) - write TileConfiguration to disk, start Grid/Collection Stitching
		// - read imp[] array, start stitching via API (when 2d multipos, or MIP)
		// 2) - do stitching

		// if (nSeries == 1 && zSize == nPositions) => 2D stitching

		// FIXME Add functionality here
		// Write TileConfiguration.txt from stg
		// if compute and viaMIP: generate MIP in memory
		// Start stitching by calling to imp[] array
		/*
		 * Use cases:
		 * - Multi-region tile stitching using fmi-faim/visiview-scripts
		 * - Tile acquisition with fixed overlap and grid (rows/cols)
		 *
		 * Modalities:
		 * - Single-channel/Multi-channel
		 * - Compute overlap on MIP
		 * - Fuse MIP/full dataset
		 */

	}

	@Nullable
	private Img<FloatType> getIlluminationField(
		final IntervalView<FloatType> tile_mips, final int dapi_channel_index)
	{

		final ImagePlus mip_stack = ImageJFunctions.wrap(Views.moveAxis(Views
			.addDimension(tile_mips, 0, 0), 3, 2), "tile_mip_stack");

		final ImagePlus illumination_field = createMIP(mip_stack,
			ZProjector.MAX_METHOD);

		final Localizable center_point = new Point(new long[] { illumination_field
			.getProcessor().getWidth() / 2, illumination_field.getProcessor()
				.getHeight() / 2 });

		logService.info("Fitting Gaussian to estimate illumination field...");
		final Gaussian gaussian = new Gaussian();
		final PeakFitter fitter = new PeakFitter(ImagePlusAdapter.wrap(
			illumination_field), Arrays.asList(center_point),
			new LevenbergMarquardtSolver(), gaussian, new MLGaussianEstimator(
				illumination_field.getProcessor().getWidth() / 3.0, 2));

		fitter.checkInput();
		fitter.process();

		double[] gaussian_params = (double[]) fitter.getResult().get(center_point);
		logService.info("Fitted Gaussian parameters:");
		logService.info(" - X position: " + String.format("%.2f",
			gaussian_params[0]));
		logService.info(" - Y position: " + String.format("%.2f",
			gaussian_params[1]));
		logService.info(" - Amplitude : " + String.format("%.2f",
			gaussian_params[2]));
		logService.info(" - Sigma     : " + String.format("%.2f", 1. / Math.sqrt(
			2. * gaussian_params[3])));

		final Img<FloatType> fitted_illumination_field = ImagePlusAdapter
			.convertFloat(illumination_field.duplicate());
		final double[] pos = new double[2];
		LoopBuilder.setImages(Intervals.positions(fitted_illumination_field),
			fitted_illumination_field).forEachPixel((position, pixel) -> {
				position.localize(pos);
				pixel.setReal(gaussian.val(pos, gaussian_params));
			});
		return fitted_illumination_field;
	}

	private ImagePlus createMIP(final ImagePlus imp, final int method) {
		ZProjector zp = new ZProjector(imp);
		zp.setMethod(method);
		zp.setStopSlice(imp.getNSlices());
		zp.doHyperStackProjection(false);
		imp.close();
		return zp.getProjection();
	}

	@SuppressWarnings("unused")
	private void initializeDialog() {
		layout = new BufferedImage(LAYOUT_WIDTH, LAYOUT_HEIGHT,
			BufferedImage.TYPE_INT_RGB);
	}

	@SuppressWarnings("unused")
	private void ndFileChanged() {
		ndFileChanged = true;
		updateNdFileInfo();
		if (stgRequired && !stgFileChanged) autoupdateStgFileParameter();
	}

	@SuppressWarnings("unused")
	private void stgFileChanged() {
		stgFileChanged = true;
		if (!ndFileChanged) autoupdateNdFileParameter();
		updateStgFileInfo();
	}

	@SuppressWarnings("unused")
	private void xSpacingChanged() {
		yCal = xCal;
		ySpacingChanged();
	}

	private void ySpacingChanged() {
		doOverrideCalibration = true;
		updateStgFileInfo();
	}

	private void autoupdateStgFileParameter() {
		if (ndFile == null || !ndFile.exists()) {
			ndMessage =
				"<html><p style=\"color:red\">Not a valid nd file.</p></html>";
			return;
		}
		stgFile = VisiviewUtils.getMatchingStgForNd(ndFile);
		if (stgFile == null) {
			stgMessage =
				"<html><p style=\"color:red\">No matching stg file found.</p></html>";
			return;
		}
		stgMessage =
			"<html><p style=\"color:green\">The stg file path was updated automatically.</p></html>";
		updateStgFileInfo();
	}

	private void autoupdateNdFileParameter() {
		if (stgFile == null || !stgFile.exists()) {
			stgMessage =
				"<html><p style=\"color:red\">Not a valid stg file.</p></html>";
			return;
		}
		ndFile = VisiviewUtils.getMatchingNdForStg(stgFile);
		if (ndFile == null) {
			ndMessage =
				"<html><p style=\"color:red\">No matching nd file found.</p></html>";
			return;
		}
		ndMessage =
			"<html><p style=\"color:green\">The nd file path was updated automatically.</p></html>";
		updateNdFileInfo();
	}

	private void updateNdFileInfo() {
		if (ndFile == null) return;
		validDatasetInfo = false;
		ndMessage = "parsing nd file..."; // TODO update dialog on separate thread?
		// TODO use scifio.initialize() and scifio.translate() with OMEMetadata here
		// to get series names

		IMetadata omeMeta = MetadataTools.createOMEXMLMetadata();
		try (ImageReader reader = new ImageReader()) {
			reader.setMetadataStore(omeMeta);
			reader.setId(ndFile.getAbsolutePath());
		}
		catch (FormatException exc) {
			logService.debug("No compatible format", exc);
			ndMessage = "No compatible format";
		}
		catch (IOException exc) {
			logService.debug("Error parsing nd file", exc);
			ndMessage = "Error parsing nd file";
		}
		nSeries = omeMeta.getImageCount();
		xSize = omeMeta.getPixelsSizeX(0).getValue();
		ySize = omeMeta.getPixelsSizeY(0).getValue();
		zSize = omeMeta.getPixelsSizeZ(0).getValue();
		nChannels = omeMeta.getPixelsSizeC(0).getValue();
		nTimepoints = omeMeta.getPixelsSizeT(0).getValue();

		xCal = (Double) omeMeta.getPixelsPhysicalSizeX(0).value();
		yCal = (Double) omeMeta.getPixelsPhysicalSizeY(0).value();
		if (zSize > 1 && nSeries > 1 && omeMeta.getPixelsPhysicalSizeZ(0) != null)
			zCal = (Double) omeMeta.getPixelsPhysicalSizeZ(0).value();

		positionNames = new ArrayList<>();
		for (int i = 0; i < nSeries; i++) {
			String currentPositionName = omeMeta.getImageName(i);
			positionNames.add(currentPositionName);
			logService.debug("Position " + i + ": " + currentPositionName);
		}

		stgRequired = !VisiviewUtils.positionsMatchGridPattern(positionNames);

		if (!stgRequired) {
			logService.debug("Position names match Row#_Col# pattern");
			pixelPositions = VisiviewUtils.positionsFromNames(positionNames, xSize,
				ySize);

			StitchingUtils.drawPositions(layout, pixelPositions, xSize, ySize);
			stgMessage =
				"<html><font style=\"color:green\">No stage position file required.</font></html>";
		}

		ndMessage = "<html>This dataset contains <font style=\"color:green\">" +
			nSeries + "</font> series.</html>";
		validDatasetInfo = true;

		/* SCIFIO version 2
		try {
			Metadata metadata = scifio.initializer().parseMetadata(ndFile.getAbsolutePath());
			OMEMetadata omeMeta = new OMEMetadata(getContext());
			scifio.translator().translate(metadata, omeMeta, true);
		
			OMEXMLMetadata omeRoot = omeMeta.getRoot();
		
			nSeries = omeRoot.getImageCount();
		
			// get size and calibration from *first* series only
			xSize = omeRoot.getPixelsSizeX(0).getValue();
			ySize = omeRoot.getPixelsSizeY(0).getValue();
			zSize = omeRoot.getPixelsSizeZ(0).getValue();
			nChannels = omeRoot.getChannelCount(0);
			//nTimepoints = omeRoot.getTimestampAnnotationCount(); // correct?
			xCal = (Double) omeRoot.getPixelsPhysicalSizeX(0).value();
			yCal = (Double) omeRoot.getPixelsPhysicalSizeY(0).value();
			zCal = (Double) omeRoot.getPixelsPhysicalSizeZ(0).value();
		
			//omeRoot.getImageName(0); // FIX ME get series names, populate gridPositions if applicable
			logService.error(omeMeta.get(0).getName());
			logService.error(metadata.get(0).getName());
		
			positionNames = new ArrayList<>();
			for (int i = 0; i< nSeries; i++) {
				positionNames.add(omeRoot.getImageName(i));
				logService.error("Position " + i + ": " + omeRoot.getImageName(i));
			}
		
			gridPositions = new ArrayList<>();
			if (positionNames.get(0).startsWith("Row")) {
				logService.error("starts with 'Row'");
			}
		
			ndMessage = "<html>This dataset contains <font style=\"color:green\">" +
					nSeries + "</font> series.</html>";
				validDatasetInfo = true;
		}
		catch (IOException exc) {
			logService.debug("Error parsing nd file", exc);
			ndMessage = "Error parsing nd file";
		}
		catch (FormatException exc) {
			logService.debug("No compatible format", exc);
			ndMessage = "No compatible format";
		}
		*/

		/* SCIFIO version 1
		try {
			Format format = formatService.getFormat(ndFile.getAbsolutePath());
			Metadata metadata = format.createParser().parse(ndFile);
			nSeries = metadata.getImageCount();
			ImageMetadata imageMetadata = metadata.get(0);
		
			xSize = imageMetadata.getAxisLength(Axes.X);
			ySize = imageMetadata.getAxisLength(Axes.Y);
			zSize = imageMetadata.getAxisLength(Axes.Z);
			nChannels = imageMetadata.getAxisLength(Axes.CHANNEL);
			nTimepoints = imageMetadata.getAxisLength(Axes.TIME);
		
			xCal = imageMetadata.getAxis(Axes.X).averageScale(0, 1);
			yCal = imageMetadata.getAxis(Axes.Y).averageScale(0, 1);
			zCal = imageMetadata.getAxis(Axes.Z).averageScale(0, 1);
		
			// List<CalibratedAxis> axes = imageMetadata.getAxes();
			ndMessage = "<html>This dataset contains <font style=\"color:green\">" +
				nSeries + "</font> series.</html>";
			validDatasetInfo = true;
		}
		catch (FormatException exc) {
			logService.debug("No compatible format", exc);
			ndMessage = "No compatible format";
		}
		catch (IOException exc) {
			logService.debug("Error parsing nd file", exc);
			ndMessage = "Error parsing nd file";
		}
		*/
	}

	private void updateStgFileInfo() {
		try {
			pixelPositions = VisiviewUtils.positionsFromStgFile(stgFile, xCal, yCal);
			StitchingUtils.drawPositions(layout, pixelPositions, xSize, ySize);
		}
		catch (IOException exc) {
			stgMessage = "Error parsing stg file";
			logService.debug("Error parsing stg file", exc);
		}
	}

	private String writeTileConfiguration(File file, ArrayList<float[]> positions,
		boolean is2d)
	{
		// TODO write TileConfiguration.txt file (using Stitching API?)
		File tileConfigFile = new File(file.getParentFile(), file.getName() +
			"_TileConfiguration.txt");
		ArrayList<String> lines = new ArrayList<>();

		// Header

		// Positions

		try {
			Files.write(tileConfigFile.toPath(), lines, Charset.forName("UTF-8"));
		}
		catch (IOException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		}

		return tileConfigFile.getAbsolutePath();
	}

}
