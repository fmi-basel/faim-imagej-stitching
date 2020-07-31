package ch.fmi.stitching;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.Concatenator;
import ij.plugin.HyperStackConverter;
import loci.formats.FormatException;
import loci.plugins.BF;

public class IOUtils {
	
	private IOUtils() {
		// prevent instantiation of static utility class
	}

	/**
	 * Load a single hyperstack {@link ImagePlus} from a list of files.
	 * <p>
	 * The file list is expected to be ordered by channels, then timepoints, e.g.:
	 * <ul>
	 * <li>image_w1gfp_t1.stk</li>
	 * <li>image_w2rfp_t1.stk</li>
	 * <li>image_w1gfp_t2.stk</li>
	 * <li>image_w2rfp_t2.stk</li>
	 * </ul>
	 * </p>
	 * <p>
	 * Each single file is expected to contain a z stack
	 * </p>
	 * 
	 * @param fileList List of files containing a z-stack each
	 * @param nChannels Number of separate channels contained in the file list
	 * @param nTimepoints Number of separate timepoints contained in the file list
	 * @return Hyperstack containing {@code z * nChannels * nTimepoints} slices
	 */
	public static ImagePlus loadTiffZCT(List<File> fileList, int nChannels, int nTimepoints) {
		ImagePlus[] imageArray = new ImagePlus[nChannels * nTimepoints];
		int i=0;
		for (File file : fileList) {
			imageArray[i++] = IJ.openImage(file.getAbsolutePath());
		}
		ImagePlus concatenatedStack = new Concatenator().concatenate(imageArray, false);
		// TODO make sure a combination of channels and timepoints is handled correctly
		new HyperStackConverter().shuffle(concatenatedStack, HyperStackConverter.ZCT);
		return concatenatedStack;
	}

	/**
	 * Load a single hyperstack {@link ImagePlus} from a list of files using Bio-Formats.
	 * 
	 * @param fileList List of files containing a z-stack each
	 * @return Hyperstack containing {@code fileList.size()} channels
	 * @throws IOException 
	 * @throws FormatException 
	 */
	public static ImagePlus loadZC(Collection<File> fileList) throws FormatException, IOException {
		ImagePlus[] imageArray = new ImagePlus[fileList.size()];
		int i=0;
		for (File file : fileList) {
			imageArray[i++] = BF.openImagePlus(file.getAbsolutePath())[0];
		}
		ImagePlus concatenatedStack = new Concatenator().concatenate(imageArray, false);
		new HyperStackConverter().shuffle(concatenatedStack, HyperStackConverter.ZCT);
		return concatenatedStack;
	}
}
