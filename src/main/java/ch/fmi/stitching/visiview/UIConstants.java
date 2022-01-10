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
