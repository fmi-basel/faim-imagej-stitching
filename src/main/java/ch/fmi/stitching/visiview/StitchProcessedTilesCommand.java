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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

import ch.fmi.stitching.IOUtils;
import ch.fmi.stitching.StitchingUtils;
import ij.ImagePlus;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import mpicbg.models.InvertibleBoundable;

/**
 * 
 * @author Jan Eglinger
 *
 */
@Plugin(type = Command.class, headless = true,
	menuPath = "FMI>VisiView Data>Stitch Preprocessed Tiles",
	initializer = "initializeDialog")
public class StitchProcessedTilesCommand extends DynamicCommand {

	private static final String EXTENSION = ".ics";
	private static final String SUFFIX_PATTERN = "_?\\d*_w(\\d+).*_s(\\d+).*";

	@Parameter(label = "Stage position file (stg)", style = "extensions:stg",
		callback = "stgFileChanged", required = false, persist = false)
	File stgFile;

	@Parameter(label = " ", visibility = ItemVisibility.MESSAGE, persist = false,
		required = false)
	private String stgMessage = " ";

	@Parameter(label = "Image data directory", style = FileWidget.DIRECTORY_STYLE,
		callback = "folderChanged", persist = false)
	File folder;

	@Parameter(label = " ", visibility = ItemVisibility.MESSAGE, persist = false,
		required = false)
	private String folderMessage = " ";

	@Parameter(label = "Overlap computation mode", style = "radioButtonVertical", //
		choices = { COMPUTE_NONE, COMPUTE_VIA_MIP, COMPUTE_FULL }, required = false)
	private String stitchingMode = COMPUTE_NONE;

	@Parameter(label = "Output", style = "radioButtonVertical", //
		choices = { OUTPUT_TXT, OUTPUT_MIP, OUTPUT_FULL }, required = false)
	private String outputMode = OUTPUT_FULL;

	@Parameter(label = "Save RAM at the cost of speed", required = false)
	private boolean saveRAM = false;

	@Parameter(label = "Override calibration metadata with provided values",
		required = false, callback = "overrideChanged")
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
	private LogService logService;

	private boolean folderChanged = false;

	private String fileNamePrefix;

	private TreeMap<Integer, TreeMap<Integer, File>> fileMap;
	private ArrayList<ImagePlus> images;
	private List<float[]> pixelPositions;
	private long xSize;
	private long ySize;
	private long zSize;
	private Double xCalMetadata;
	private Double yCalMetadata;
	private Double zCalMetadata;

	@Override
	public void run() {
		// Stitch preprocessed (e.g. deconvolved) tiles using stage coordinate info from stg file
		// imageList = 
		images = new ArrayList<>();
		// TODO make sure files are sorted correctly
		fileMap.values().forEach(m -> {
			try {
				images.add(IOUtils.loadZC(m.values()));
			}
			catch (FormatException exc) {
				// TODO Auto-generated catch block
				exc.printStackTrace();
			}
			catch (IOException exc) {
				// TODO Auto-generated catch block
				exc.printStackTrace();
			}
		});

		// models = 
		ArrayList<InvertibleBoundable> models = StitchingUtils.computeStitching(images, pixelPositions, 3, !stitchingMode.equals(COMPUTE_NONE), saveRAM);

		// fused = ...
		fused = StitchingUtils.fuseTiles(images, models, 3);
	}

	@SuppressWarnings("unused")
	private void initializeDialog() {
		layout = new BufferedImage(LAYOUT_WIDTH, LAYOUT_HEIGHT,
			BufferedImage.TYPE_INT_RGB);
	}

	@SuppressWarnings("unused")
	private void stgFileChanged() {
		fileNamePrefix = stgFile.getName().substring(0, stgFile.getName().lastIndexOf('.'));
		if (!folderChanged) {
			autoUpdateFolder();
		}

		populateFileMap();

		// get positions
		// update layout
	}

	@SuppressWarnings("unused")
	private void folderChanged() {
		folderChanged = true;
		populateFileMap();
		// parse first file
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

	private void overrideChanged() {
		if (!doOverrideCalibration) {
			xCal = xCalMetadata;
			yCal = yCalMetadata;
			zCal = zCalMetadata;
			updateStgFileInfo();
		}
	}

	private void autoUpdateFolder() {
		folder = stgFile.getParentFile();
	}

	private void populateFileMap() {
		if (fileNamePrefix == null || folder == null || fileNamePrefix == "") {
			return;
		}
		File[] imageFileList = folder.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(fileNamePrefix) && (name.endsWith(EXTENSION));
			}
		});
		// report nChannels nPositions
		SortedSet<Integer> channelIds = new TreeSet<>(); // ? necessary?
		SortedSet<Integer> positionIds = new TreeSet<>();
		Pattern pattern = Pattern.compile(SUFFIX_PATTERN);
		// generate file map: positions>channels>file
		fileMap = new TreeMap<>();
		for (File f : imageFileList) {
			Matcher m = pattern.matcher(f.getName().substring(fileNamePrefix.length()));
			if (m.matches()) {
				int channelId = Integer.parseInt(m.group(1));
				int positionId = Integer.parseInt(m.group(2));
				channelIds.add(channelId);
				if (positionIds.add(positionId)) {
					fileMap.put(positionId, new TreeMap<>());
				}
				fileMap.get(positionId).put(channelId, f);
			}
		}
		folderMessage = "Found " + imageFileList.length + " files (" + channelIds.size() + " channels, " + positionIds.size() + " positions)";
		logService.debug("Created file map " + fileMap);
		// logService.debug(new JSONObject(fileMap).toString(2));
		// parse xSize ySize xCalAuto yCalAuto zCalAuto from first file
		updateMetadata(fileMap.firstEntry().getValue().firstEntry().getValue());
		overrideChanged(); // => update stgFileInfo
		// TODO warn if positionIds.size() != pixelPositions.size()
	}

	private void updateMetadata(File imageFile) {
		IMetadata omeMeta = MetadataTools.createOMEXMLMetadata();
		try (ImageReader reader = new ImageReader()) {
			reader.setMetadataStore(omeMeta);
			reader.setId(imageFile.getAbsolutePath());
		}
		catch (FormatException exc) {
			logService.error("No compatible image format", exc);
		}
		catch (IOException exc) {
			logService.error("Error parsing metadata in first image file", exc);
		}
		xSize = omeMeta.getPixelsSizeX(0).getValue();
		ySize = omeMeta.getPixelsSizeY(0).getValue();
		zSize = omeMeta.getPixelsSizeZ(0).getValue();

		xCalMetadata = (Double) omeMeta.getPixelsPhysicalSizeX(0).value();
		yCalMetadata = (Double) omeMeta.getPixelsPhysicalSizeY(0).value();
		if (zSize > 1) zCalMetadata = (Double) omeMeta.getPixelsPhysicalSizeZ(0).value();
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

}
