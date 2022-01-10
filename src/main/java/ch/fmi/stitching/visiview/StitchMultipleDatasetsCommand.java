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

import io.scif.FilePattern;
import io.scif.services.FilePatternService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ch.fmi.stitching.StitchingUtils;
import ij.ImagePlus;
import loci.formats.FormatException;
import loci.plugins.BF;
import mpicbg.models.InvertibleBoundable;

@Plugin(type = Command.class, headless = true,
	menuPath = "FMI>VisiView Data>Stitch Multiple Datasets as Tiles")
public class StitchMultipleDatasetsCommand extends DynamicCommand {

	@Parameter(label = "Representative file", style = "open", callback = "fileChanged", persist = false)
	private File inputFile;
	// callback: list files with same extension, detect similarities, suggest series number pattern

	@Parameter(label = "File name pattern", callback = "patternChanged", persist = false)
	private String pattern;

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String message = "Please select a file.";

	@Parameter(label = "Save RAM at the cost of speed", required = false)
	private boolean saveRAM = true;

	@Parameter
	private FilePatternService filePatternService;

	@Parameter
	private LogService logService;

	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus fused;

	private List<File> fileList;

	@Override
	public void run() {
		// list all files matching pattern in parent folder
		ArrayList<ImagePlus> imageList = new ArrayList<>();
		int dimensionality = 2;
		// open all images
		try {
			for (File file : fileList) {
				ImagePlus[] imps = BF.openImagePlus(file.getAbsolutePath());
				if (imps[0].getNSlices() > 1) dimensionality = 3;
				imageList.add(imps[0]);
			}
		}
		catch (FormatException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		}
		catch (IOException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		}

		// Compute stitching
		float[] initialPosition = {0, 0, 0};
		ArrayList<InvertibleBoundable> models = StitchingUtils.computeStitching(imageList, Collections.nCopies(imageList.size(), initialPosition), dimensionality, true, saveRAM);

		// Fuse images
		fused = StitchingUtils.fuseTiles(imageList, models, dimensionality);
	}

	// -- Callback methods --

	@SuppressWarnings("unused")
	private void fileChanged() {
		pattern = filePatternService.findPattern(inputFile);
		patternChanged();
	}

	private void patternChanged() {
		if (inputFile == null) {
			return;
		}
		FilePattern filePattern = new FilePattern(getContext(), pattern);
		fileList = Arrays.stream(filePattern.getFiles()).map(path -> new File(path)).filter(f -> f.exists()).collect(Collectors.toList());
		// update file list
		// update message
		message = fileList.size() + " file(s) matching pattern.";
	}
}
