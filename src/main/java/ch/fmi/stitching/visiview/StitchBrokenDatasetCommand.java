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

package ch.fmi.stitching.visiview;

import static ch.fmi.stitching.visiview.UIConstants.COMPUTE_FULL;
import static ch.fmi.stitching.visiview.UIConstants.COMPUTE_NONE;
import static ch.fmi.stitching.visiview.UIConstants.COMPUTE_VIA_MIP;
import static ch.fmi.stitching.visiview.UIConstants.LAYOUT_HEIGHT;
import static ch.fmi.stitching.visiview.UIConstants.LAYOUT_WIDTH;
import static ch.fmi.stitching.visiview.UIConstants.OUTPUT_FULL;
import static ch.fmi.stitching.visiview.UIConstants.OUTPUT_MIP;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ch.fmi.stitching.StitchingUtils;
import ij.ImagePlus;
import mpicbg.models.InvertibleBoundable;

@Plugin(type = Command.class, headless = true,
	menuPath = "FMI>VisiView Data>Stitch Unreadable VisiView Dataset")
public class StitchBrokenDatasetCommand extends DynamicCommand {

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
		choices = { COMPUTE_NONE, COMPUTE_VIA_MIP, COMPUTE_FULL }, required = false)
	private String stitchingMode = COMPUTE_NONE;

	@Parameter(label = "Output", style = "radioButtonVertical", //
			choices = { OUTPUT_MIP, OUTPUT_FULL }, required = false)
		private String outputMode = OUTPUT_FULL;

	@Parameter(label = "Channels (e.g. 2-4 or 1,4 - leave empty for all)", required = false)
	private String channelSelection = "";

	@Parameter(label = "Pixel spacing (x)", callback = "xSpacingChanged")
	private Double xCal;

	@Parameter(label = "Pixel spacing (y)", callback = "ySpacingChanged")
	private Double yCal;

	@Parameter(label = "Pixel spacing (z)")
	private Double zCal;

	@Parameter(required = false, persist = false)
	private BufferedImage layout = new BufferedImage(LAYOUT_WIDTH, LAYOUT_HEIGHT,
		BufferedImage.TYPE_INT_RGB);

	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus fused;

	@Parameter
	private LogService logService;

	private boolean ndFileChanged;
	private boolean stgFileChanged;
	private List<String> positionNames;
	private List<List<File>> stkFileList; // List of stk files in sets of positions
	private ArrayList<ImagePlus> images; // ArrayList required by stitching API
	private List<float[]> pixelPositions;
	private long xSize = 100;
	private long ySize = 100;
	private boolean stgRequired;

	private boolean validDatasetInfo;

	private int nChannels;

	private int nFrames;

	@Override
	public void run() {
		if (validDatasetInfo) {
			logService.info("now stitching...");
			logService.debug(stkFileList);

			images = new ArrayList<>();
			for (List<File> positionList : stkFileList) {
				images.add(VisiviewUtils.loadPositionStack(positionList, nChannels, nFrames));
			}

			// TODO handle overlap computation via MIP
			// TODO support output option MIP
			ArrayList<InvertibleBoundable> models = StitchingUtils.computeStitching(images, pixelPositions, 3, !stitchingMode.equals(COMPUTE_NONE));
			fused = StitchingUtils.fuseTiles(images, models, 3);
			// TODO set output calibration and hyperstack dimensions
		}
	}

	@SuppressWarnings("unused")
	private void ndFileChanged() {
		ndFileChanged = true;
		if (!stgFileChanged) autoupdateStgFileParameter();
		updateNdFileInfo();
	}

	@SuppressWarnings("unused")
	private void stgFileChanged() {
		stgFileChanged = true;
		if (!ndFileChanged) autoupdateNdFileParameter();
		updateStgFileInfo();
	}

	private void autoupdateStgFileParameter() {
		if (ndFile == null || !ndFile.exists()) {
			ndMessage = "<html><p style=\"color:red\">Not a valid nd file.</p></html>";
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
			stgMessage = "<html><p style=\"color:red\">Not a valid stg file.</p></html>";
			return;
		}
		ndFile = VisiviewUtils.getMatchingNdForStg(stgFile);
		if (ndFile == null) {
			ndMessage = "<html><p style=\"color:red\">No matching nd file found.</p></html>";
			return;
		}
		ndMessage =
			"<html><p style=\"color:green\">The nd file path was updated automatically.</p></html>";
		updateNdFileInfo();
	}

	private void updateNdFileInfo() {
		// parse nd file
		try {
			Map<String, String> datasetMap = VisiviewUtils.parseNdFile(ndFile);
			nChannels = VisiviewUtils.getNChannels(datasetMap);
			nFrames = VisiviewUtils.getNFrames(datasetMap);
			// populate file list
			stkFileList = VisiviewUtils.getCompanionFiles(ndFile, datasetMap);
			// get xSize and ySize from first tile
			if (stkFileList != null) {
				int[] dims = VisiviewUtils.getTiffDimensions(stkFileList.get(0).get(0));
				xSize = dims[0];
				ySize = dims[1];
			}

			// populate position names
			positionNames = VisiviewUtils.getPositionNames(datasetMap);
			stgRequired = !VisiviewUtils.positionsMatchGridPattern(positionNames);
			// populate pixel positions (if available from grid)
			if (!stgRequired) {
				logService.debug("Position names match Row#_Col# pattern");
				pixelPositions = VisiviewUtils.positionsFromNames(positionNames, xSize, ySize);
				stgMessage =
					"<html><font style=\"color:green\">No stage position file required.</font></html>";
			}
			ndMessage = "<html>This dataset contains <font style=\"color:green\">" +
					positionNames.size() + "</font> series.</html>";
			StitchingUtils.drawPositions(layout, pixelPositions, xSize, ySize);
			validDatasetInfo = true;
		}
		catch (IOException exc) {
			logService.debug("Error parsing dataset", exc);
			ndMessage = "Error parsing dataset";
		}
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

	@SuppressWarnings("unused")
	private void xSpacingChanged() {
		yCal = xCal;
		ySpacingChanged();
	}

	private void ySpacingChanged() {
		updateStgFileInfo();
	}

}
