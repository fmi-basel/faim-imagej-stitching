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
