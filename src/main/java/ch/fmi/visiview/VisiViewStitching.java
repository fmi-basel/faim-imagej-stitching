/*-
 * #%L
 * ImageJ plugins for processing VisiView datasets
 * %%
 * Copyright (C) 2019 - 2020 Friedrich Miescher Institute for Biomedical
 * 			Research, Basel
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package ch.fmi.visiview;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.ZProjector;
import io.scif.SCIFIO;
import io.scif.services.FormatService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import mpicbg.models.InvertibleBoundable;
import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ch.fmi.stitching.StitchingUtils;

@Plugin(type = Command.class, headless = true,
	menuPath = "FMI>VisiView Data>Stitch Dataset (default)",
	initializer = "initializeDialog")
public class VisiViewStitching extends DynamicCommand {

	private static final String QUICK = "Quick (do not compute overlap)";
	private static final String VIA_MIP = "Compute overlap on maximum projection";
	private static final String FULL = "Compute overlap on full volume";

	private static final String TXT_ONLY = "Coordinates text file only";
	private static final String MIP_ONLY = "Maximum projection only";
	private static final String FULL_OUTPUT = "Full volume output";

	private static final Pattern ndFilePattern = Pattern.compile("(.*_)\\d\\.nd");
	private static final Pattern stgFilePattern = Pattern.compile("(.*_)\\.stg");

	private static final int LAYOUT_WIDTH = 256;
	private static final int LAYOUT_HEIGHT = 256;

	private static final float VISIVIEW_OVERLAP_FACTOR = 0.1f;

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
		choices = { QUICK, VIA_MIP, FULL }, required = false)
	private String stitchingMode = QUICK;

	@Parameter(label = "Output", style = "radioButtonVertical", //
		choices = { TXT_ONLY, MIP_ONLY, FULL_OUTPUT }, required = false)
	private String outputMode = FULL_OUTPUT;

	@Parameter(label = "Override calibration metadata with provided values",
		required = false)
	private Boolean doOverrideCalibration = false;

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

	private ArrayList<float[]> pixelPositions;  // holds the pixel-based positions
	private ArrayList<String> positionNames;
	private ArrayList<int[]> gridPositions; // holds row and column positions (if applicable)
	private ArrayList<ImagePlus> images;
	private ArrayList<InvertibleBoundable> models;

	private boolean stgRequired;

	private boolean ndFileChanged = false;
	private boolean stgFileChanged = false;

	private boolean validDatasetInfo = false;

	@Override
	public void run() {
		logService.debug("Now running...");

		// Ensure valid input parameters
		if (!doOverrideCalibration && !validDatasetInfo) updateNdFileInfo();
		if (stgRequired && stgFile != null && stgFile.exists()) {
			updateStgFileInfo();
		} else if (stgRequired) {
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
						images.add(d.run(imps[0], 1, imps[0].getNChannels(), i+1, i+1, 1, imps[0].getNFrames()));
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

				models = StitchingUtils.computeStitching(images, pixelPositions, 2, stitchingMode.equals(QUICK) ? false : true);

				fused = StitchingUtils.fuseTiles(images, models, 2);

				//fused.setTitle(imps[0].getTitle() + "_fused");
				fused.setTitle("Fused");
				Calibration cal = new Calibration();
				cal.pixelWidth = xCal;
				cal.pixelHeight = yCal;
				cal.pixelDepth = zCal;
				fused.setCalibration(cal);
				
				// TODO close all images?
			} else if (stitchingMode.equals(VIA_MIP) || outputMode.equals(MIP_ONLY)) {
				// create MIPs for all series, stitch online
				// parameters imp[], positions
				logService.error("Stitching MIPs");

				// read each series, create MIP, add to list
				try {
					images = new ArrayList<>();

					ImporterOptions options = new ImporterOptions();
					options.setOpenAllSeries(false);
					options.setId(ndFile.getAbsolutePath());
					options.setSeriesOn(0, true);
					for (int i = 0; i < nSeries; i++) {
						images.add(createMIP(BF.openImagePlus(options)[0]));
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

				models = StitchingUtils.computeStitching(images, pixelPositions, 2, stitchingMode.equals(QUICK) ? false : true);

				// case: via MIP: go on with full dataset
				// load all full series into imps[]
				// case: MIP output: fuse

				fused = StitchingUtils.fuseTiles(images, models, 2);

				// TODO set title and calibration

			} else { // stitch with TileConfiguration.txt file
				//String tileConfigPath = writeTileConfiguration(ndFile, pixelPositions, is2D);

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
					models = StitchingUtils.computeStitching(images, pixelPositions, is2D ? 2 : 3, stitchingMode.equals(QUICK) ? false : true);
					// fuseTiles
					fused = StitchingUtils.fuseTiles(images, models, is2D ? 2 : 3);
				} catch (FormatException exc) {
					logService.error("Error performing a file format operation", exc);
					return;
				} catch (IOException exc) {
					logService.error("Error reading file", exc);
					return;
				}
			}
		} else {
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
		//    - read imp[] array, start stitching via API (when 2d multipos, or MIP)
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

	private ImagePlus createMIP(ImagePlus imp) {
		ZProjector zp = new ZProjector(imp);
		zp.setMethod(ZProjector.MAX_METHOD);
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
		if (!stgFileChanged) autoupdateStgFileParameter();
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
			ndMessage = "Not a valid nd file.";
			return;
		}
		Matcher m = ndFilePattern.matcher(ndFile.getName());
		if (m.matches()) {
			File stgFileCandidate = new File(ndFile.getParent(), m.group(1) + ".stg");
			if (!stgFileCandidate.exists()) {
				stgMessage =
					"<html><p style=\"color:red\">No matching stg file found.</p></html>";
				return;
			}
			stgFile = stgFileCandidate;
			stgMessage =
				"<html><p style=\"color:green\">The stg file path was updated automatically.</p></html>";
			updateStgFileInfo();
		}
		else {
			stgMessage = "No matching stg file found.";
		}
	}

	private void autoupdateNdFileParameter() {
		if (stgFile == null || !stgFile.exists()) {
			stgMessage = "Not a valid stg file.";
			return;
		}
		Matcher m = stgFilePattern.matcher(stgFile.getName());
		if (m.matches()) {
			File ndFileCandidate = new File(stgFile.getParent(), m.group(1) + "1.nd");
			// TODO test more numbers, i.e. 2.nd, 3.nd, 4.nd, ...
			if (!ndFileCandidate.exists()) {
				stgMessage = "No matching nd file found.";
				return;
			}
			ndFile = ndFileCandidate;
			stgMessage =
				"<html><p style=\"color:green\">The nd file path was updated automatically.</p></html>";
			updateNdFileInfo();
		}
		else {
			stgMessage = "No matching nd file found.";
		}
	}

	private void updateNdFileInfo() {
		if (ndFile == null) return;
		validDatasetInfo = false;
		ndMessage = "parsing nd file..."; // TODO update dialog on separate thread?
		// TODO use scifio.initialize() and scifio.translate() with OMEMetadata here to get series names

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
		if (zSize > 1 && nSeries > 1) zCal = (Double) omeMeta.getPixelsPhysicalSizeZ(0).value();

		positionNames = new ArrayList<>();
		for (int i = 0; i < nSeries; i++) {
			positionNames.add(omeMeta.getImageName(i));
			logService.debug("Position " + i + ": " + omeMeta.getImageName(i));
		}

		gridPositions = new ArrayList<>();
		Pattern p = Pattern.compile("Stage\\d+ \"Row(\\d+)_Col(\\d+)\"");

		if (p.matcher(positionNames.get(0)).matches()) {
			stgRequired = false;
			logService.debug("Position names match Row#_Col# pattern");

			for (int i = 0; i < nSeries; i++) {
				Matcher m = p.matcher(positionNames.get(i));
				gridPositions.add(m.matches() ? new int[] { //
					Integer.parseInt(m.group(1)), //
					Integer.parseInt(m.group(2)) //
				} : null);
			}

			pixelPositions = new ArrayList<>();
			for (int[] pos : gridPositions) {
				pixelPositions.add(new float[] { //
					pos[1] * xSize * (1 - VISIVIEW_OVERLAP_FACTOR), //
					pos[0] * ySize * (1 - VISIVIEW_OVERLAP_FACTOR) //
				});
				// TODO check positions in different cases! (inverted x for some microscopes?)
			}

			StitchingUtils.drawPositions(layout, pixelPositions, xSize, ySize);

			stgMessage =
				"<html><font style=\"color:green\">No stage position file required.</font></html>";
		} else {
			stgRequired = true;
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
		pixelPositions = new ArrayList<>();
		try {
			Files.lines(stgFile.toPath()).skip(4).forEachOrdered(line -> pixelPositions
				.add(positionFromLine(line, xCal, yCal)));
			StitchingUtils.drawPositions(layout, pixelPositions, xSize, ySize);
		}
		catch (IOException exc) {
			stgMessage = "Error parsing stg file";
			logService.debug("Error parsing stg file", exc);
		}
	}

	private float[] positionFromLine(String line, Double xCalibration,
		Double yCalibration)
	{
		String[] tokens = line.split(",");
		return new float[] { (float) (Float.parseFloat(tokens[1]) / xCalibration), (float) (Float
			.parseFloat(tokens[2]) / yCalibration) };
	}

	private String writeTileConfiguration(File file,
		ArrayList<float[]> positions, boolean is2d)
	{
		// TODO write TileConfiguration.txt file (using Stitching API?)
		File tileConfigFile = new File(file.getParentFile(), file.getName() + "_TileConfiguration.txt");
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

	public static void main(final String... args) {
		// create the ImageJ application context with all available services
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		ij.command().run(VisiViewStitching.class, true);
	}
}
