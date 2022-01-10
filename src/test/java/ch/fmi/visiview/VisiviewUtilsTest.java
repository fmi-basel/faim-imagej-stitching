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
package ch.fmi.visiview;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ch.fmi.stitching.visiview.VisiviewUtils;


public class VisiviewUtilsTest {

	@Before
	public void setUp() {
		
	}

	@After
	public void tearDown() {
		
	}

	@Test
	public void testGetCompanionFiles() {
		File ndFile = new File("fake/path/test_experiment.nd");
		Map<String, String> datasetInfo = new HashMap<String, String>() {{
			put("DoWave", " FALSE");
			put("DoStage", " TRUE");
			put("NStagePositions", " 5");
			put("DoTimelapse", " FALSE");
			put("DoZSeries", " TRUE");
		}};
		List<List<File>> fileList = VisiviewUtils.getCompanionFiles(ndFile, datasetInfo);
		System.err.println(fileList);

		File testFile1 = new File("fake/path/test_experiment_s5.stk");
		assertTrue(fileList.stream().flatMap(Collection::stream).collect(Collectors
			.toList()).contains(testFile1));
	}
}
