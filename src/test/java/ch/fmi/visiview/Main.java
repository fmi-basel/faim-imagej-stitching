
package ch.fmi.visiview;

import net.imagej.ImageJ;

/**
 * Main class for interactive testing and debugging
 *
 * @author Jan Eglinger
 */
public class Main {

	public static void main(final String... args) {
		// create the ImageJ application context with all available services
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
	}
}
