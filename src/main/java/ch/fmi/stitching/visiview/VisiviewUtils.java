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

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.TiffDecoder;
import ij.plugin.Concatenator;
import ij.plugin.HyperStackConverter;

public class VisiviewUtils {

	public static final float VISIVIEW_OVERLAP_FACTOR = 0.1f;

	private static final long STG_FIRST_POSITION_LINE_INDEX = 4;
	private static final Pattern ND_FILE_PATTERN = Pattern.compile("(.*_)\\d\\.nd");
	private static final Pattern STG_FILE_PATTERN = Pattern.compile("(.*_)\\.stg");
	private static final Pattern GRID_POSITION_PATTERN = Pattern.compile(".*Row(\\d+)_Col(\\d+).*");

	private static final String DO_WAVE = "DoWave";
	private static final String DO_STAGE = "DoStage";
	private static final String DO_TIMELAPSE = "DoTimelapse";
	private static final String DO_Z_SERIES = "DoZSeries";
	private static final String WAVE_DO_Z_PREFIX = "WaveDoZ";
	private static final String WAVE_NAME_PREFIX = "WaveName";
	private static final String N_WAVELENGTHS = "NWavelengths";
	private static final String N_STAGE_POSITIONS = "NStagePositions";
	private static final String N_TIMEPOINTS = "NTimePoints";

	private VisiviewUtils() {
		// prevent instantiation of static utility class
	}

	public static File getMatchingStgForNd(File ndFile) {
		Matcher m = ND_FILE_PATTERN.matcher(ndFile.getName());
		if (m.matches()) {
			File stgFileCandidate = new File(ndFile.getParent(), m.group(1) + ".stg");
			if (!stgFileCandidate.exists()) {
				return null;
			}
			return stgFileCandidate;
		}
		return null;
	}

	public static File getMatchingNdForStg(File stgFile) {
		Matcher m = STG_FILE_PATTERN.matcher(stgFile.getName());
		if (m.matches()) {
			File ndFileCandidate = new File(stgFile.getParent(), m.group(1) + "1.nd");
			// TODO test more numbers, i.e. 2.nd, 3.nd, 4.nd, ...
			if (!ndFileCandidate.exists()) {
				return null;
			}
			return ndFileCandidate;
		}
		return null;
	}

	/**
	 * Provide a list of companion (stk) files, given an nd file path and the
	 * corresponding metadata map. The returned list is a nested list, containing
	 * the positions in the outer list, and both wavelengths and timepoints
	 * (CT-ordered) in the inner list.
	 * 
	 * @param ndFile
	 * @param datasetInfo
	 * @return List of positions containing a list of all stk files (all
	 *         wavelengths and all time points) per position.
	 */
	public static List<List<File>> getCompanionFiles(File ndFile,
		Map<String, String> datasetInfo)
	{
		List<List<File>> stkList = new ArrayList<>();
		File parent = ndFile.getParentFile();
		// prefix
		String ndFileName = ndFile.getName();
		String prefix = ndFileName.substring(0, ndFileName.lastIndexOf("."));
		// channel suffix
		int nWavelengths = getNdParameter(datasetInfo, DO_WAVE, N_WAVELENGTHS);
		// stage positions
		int nStagePositions = getNdParameter(datasetInfo, DO_STAGE, N_STAGE_POSITIONS);
		// time points
		int nTimePoints = getNdParameter(datasetInfo, DO_TIMELAPSE, N_TIMEPOINTS);

		for (int s = 1; s <= nStagePositions; s++) {
			List<File> positionStk = new ArrayList<>();
			for (int t = 1; t <= nTimePoints; t++) {
				for (int w = 1; w <= nWavelengths; w++) {
					String filename = prefix;
					filename += nWavelengths > 1 ? "_w" + w + datasetInfo.get(WAVE_NAME_PREFIX + w).trim() : "";
					filename += nStagePositions > 1 ? "_s" + s : "";
					filename += nTimePoints > 1 ? "_t" + t : "";
					boolean isStack = getNdBoolean(datasetInfo, DO_Z_SERIES) &&
							(nWavelengths == 1 || getNdBoolean(datasetInfo, WAVE_DO_Z_PREFIX + w));
					filename += isStack ? ".stk" : ".tif";
					positionStk.add(new File(parent, filename));
					//System.out.println(filename);
				}
			}
			stkList.add(positionStk);
		}
		// TODO respect WaveInFileName? (for multi-channel only; this is true for all datasets known at FMI)
		return stkList;
	}

	public static int[] getTiffDimensions(File file) throws IOException {
		FileInfo[] info = new TiffDecoder(file.getParent(), file.getName()).getTiffInfo();
		int[] dims = new int[2];
		dims[0] = info[0].width;
		dims[1] = info[0].height;
		return dims;
	}

	public static ImagePlus loadPositionStack(List<File> fileList, int nChannels, int nTimepoints) {
		ImagePlus[] imageArray = new ImagePlus[nChannels * nTimepoints];
		int i=0;
		for (File file : fileList) {
			imageArray[i++] = IJ.openImage(file.getAbsolutePath());
		}
		ImagePlus concatenatedStack = new Concatenator().concatenate(imageArray, false);
		new HyperStackConverter().shuffle(concatenatedStack, HyperStackConverter.ZCT);
		return concatenatedStack;
	}

	public static int getNChannels(Map<String, String> info) {
		return getNdParameter(info, DO_WAVE, N_WAVELENGTHS);
	}

	public static int getNFrames(Map<String, String> info) {
		return getNdParameter(info, DO_TIMELAPSE, N_TIMEPOINTS);
	}

	private static boolean getNdBoolean(Map<String, String> info, String flag) {
		return Boolean.parseBoolean(info.get(flag).trim());
	}

	private static int getNdParameter(Map<String, String> info, String doString, String nString) {
		System.out.println(doString);
		if (!Boolean.parseBoolean(info.get(doString).trim()))
			return 1;
		return Integer.parseInt(info.get(nString).trim());
	}

	public static List<String> getPositionNames(Map<String, String> datasetInfo) {
		int nSeries = Integer.parseInt(datasetInfo.get("NStagePositions").trim());
		ArrayList<String> positionNames = new ArrayList<>();
		for (int i = 1; i <= nSeries; i++) {
			positionNames.add(datasetInfo.get("Stage" + i).trim());
		}
		return positionNames;
	}

	public static Map<String, String> parseNdFile(File ndFile) throws IOException {
		try (FileReader fileReader = new FileReader(ndFile);
				CSVReader csvReader = new CSVReader(fileReader))
		{
			return csvReader.readAll().stream().filter(arr -> arr.length > 1).collect(
				Collectors.toMap(arr -> arr[0], arr -> arr[1]));
		}
		//return Files.lines(ndFile.toPath()).map(line -> parseNdLine(line)).filter(
		//	Objects::nonNull).collect(Collectors.toMap(arr -> arr[0], arr -> arr[1]));
		catch (CsvException exc) {
			// TODO Fix error reporting
			exc.printStackTrace();
			return null;
		}
	}

	public static List<float[]> positionsFromStgFile(File stgFile, double xCal,
		double yCal) throws IOException
	{
		// NB: the positions usually start at line index 4
		// This won't work for stg files containing "categories"
		// See https://mdc.custhelp.com/app/answers/detail/a_id/20065/kw/stg
		return Files.lines(stgFile.toPath()).skip(STG_FIRST_POSITION_LINE_INDEX)
			.map(line -> parseLine(line, xCal, yCal)).collect(Collectors.toList());
	}

	public static List<float[]> positionsFromNames(List<String> positionNames, long xSize, long ySize) {
		// get pixelPositions from positionNames, or null if not applicable
		return positionNames.stream().map(name -> parseGridPosition(name, xSize, ySize)).collect(Collectors.toList());
	}

	public static boolean positionsMatchGridPattern(List<String> positions) {
		for (String p : positions) {
			if (!GRID_POSITION_PATTERN.matcher(p).matches()) {
				return false;
			}
		}
		return true;
	}

	private static float[] parseGridPosition(String name, long xSize, long ySize) {
		Matcher m = GRID_POSITION_PATTERN.matcher(name);
		if (m.matches()) {
			return new float[] {
				Integer.parseInt(m.group(2)) * xSize * (1 - VISIVIEW_OVERLAP_FACTOR),
				Integer.parseInt(m.group(1)) * ySize * (1 - VISIVIEW_OVERLAP_FACTOR)				
			};
		}
		return null;
	}

	private static float[] parseLine(String line, double xCal, double yCal) {
		String[] tokens = line.split(",");
		return new float[] { (float) (Float.parseFloat(tokens[1]) / xCal),
			(float) (Float.parseFloat(tokens[2]) / yCal) };
	}
}
