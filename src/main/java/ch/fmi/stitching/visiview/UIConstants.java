
package ch.fmi.stitching.visiview;

public class UIConstants {

	protected static final String COMPUTE_NONE = "Quick (do not compute overlap)";
	protected static final String COMPUTE_VIA_MIP = "Compute overlap on maximum projection";
	protected static final String COMPUTE_FULL = "Compute overlap on full volume";

	protected static final String OUTPUT_TXT = "Coordinates text file only";
	protected static final String OUTPUT_MIP = "Maximum projection only";
	protected static final String OUTPUT_FULL = "Full volume output";

	protected static final int LAYOUT_WIDTH = 256;
	protected static final int LAYOUT_HEIGHT = 256;

	private UIConstants() {
		// prevent instantiation of static utility class
	}

}
