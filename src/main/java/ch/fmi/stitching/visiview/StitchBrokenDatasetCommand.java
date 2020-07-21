/*-
 * #%L
 * ImageJ utilities and commands for stitching various datasets
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

package ch.fmi.stitching.visiview;

import static ch.fmi.stitching.visiview.UIConstants.COMPUTE_FULL;
import static ch.fmi.stitching.visiview.UIConstants.COMPUTE_NONE;
import static ch.fmi.stitching.visiview.UIConstants.COMPUTE_VIA_MIP;
import static ch.fmi.stitching.visiview.UIConstants.LAYOUT_HEIGHT;
import static ch.fmi.stitching.visiview.UIConstants.LAYOUT_WIDTH;
import static ch.fmi.stitching.visiview.UIConstants.OUTPUT_FULL;
import static ch.fmi.stitching.visiview.UIConstants.OUTPUT_MIP;
import static ch.fmi.stitching.visiview.UIConstants.OUTPUT_TXT;

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

			// populate position names
			positionNames = VisiviewUtils.getPositionNames(datasetMap);
			stgRequired = !VisiviewUtils.positionsMatchGridPattern(positionNames);
			// populate pixel positions (if available from grid)
			if (!stgRequired) {
				logService.debug("Position names match Row#_Col# pattern");
				pixelPositions = VisiviewUtils.positionsFromNames(positionNames, xSize, ySize);
				StitchingUtils.drawPositions(layout, pixelPositions, xSize, ySize);
				stgMessage =
					"<html><font style=\"color:green\">No stage position file required.</font></html>";
			}
			ndMessage = "<html>This dataset contains <font style=\"color:green\">" +
					positionNames.size() + "</font> series.</html>";
			validDatasetInfo = true;
		}
		catch (IOException exc) {
			logService.debug("Error parsing nd file", exc);
			ndMessage = "Error parsing nd file";
		}
	}

	private void updateStgFileInfo() {
		try {
			pixelPositions = VisiviewUtils.positionsFromStgFile(stgFile, xCal, yCal);
			// TODO get xSize and ySize from first tile?
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
