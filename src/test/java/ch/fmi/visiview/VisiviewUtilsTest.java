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
