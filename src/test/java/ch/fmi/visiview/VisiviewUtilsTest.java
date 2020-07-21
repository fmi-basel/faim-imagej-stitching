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
