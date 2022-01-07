#@ File[] (style = "extensions:nd") inputFiles
#@ File (style = "directory") outputFolder
#@ String (choices = {"Full volume output", "Maximum projection only"}, label = "Output mode") outputMode
#@ String (choices = {"tif", "ics/ids", "ims"}, style = "radioButtonVertical") outputFormat
#@ Double xCal
#@ Double yCal
#@ Double zCal
#@ CommandService cs
#@ ModuleService ms
#@ SCIFIO scifio
#@ FormatService formatService
#@ LogService logService

import ch.fmi.stitching.visiview.StitchVisiviewDatasetCommand
import ch.fmi.stitching.visiview.UIConstants
import ij.IJ

inputMap = [:]
inputMap["stitchingMode"] = UIConstants.COMPUTE_NONE

// inputMap["outputMode"] = UIConstants.OUTPUT_FULL
switch (outputMode) {
	case UIConstants.OUTPUT_MIP:
		inputMap["outputMode"] = UIConstants.OUTPUT_MIP
		break
	case UIConstants.OUTPUT_FULL:
		inputMap["outputMode"] = UIConstants.OUTPUT_FULL
		break
	default:
		logService.warn("Output mode '" + outputMode + "' doesn't correspond to a valid choice.")
		inputMap["outputMode"] = UIConstants.OUTPUT_FULL
		break	
}

inputMap["doOverrideCalibration"] = true
inputMap["xCal"] = xCal
inputMap["yCal"] = yCal
inputMap["zCal"] = zCal
inputMap["scifio"] = scifio
inputMap["formatService"] = formatService
inputMap["logService"] = logService

for (ndFile in inputFiles) {
	inputMap["ndFile"] = ndFile
	// NB: see https://github.com/scijava/scijava-common/issues/407
	// module = cs.run(StitchVisiviewDatasetCommand.class, false, inputMap)
	info = cs.getCommand(StitchVisiviewDatasetCommand.class)
	module = ms.run(info, false, inputMap).get()
	resultImp = module.getOutput("fused")
	name = ndFile.getName()
	name = name[0..name.lastIndexOf(".")]
	save(resultImp, outputFormat, outputFolder, name)
	resultImp.close()
}

def save(imp, format, folder, filename) {
	switch (format) {
		case "tif":
			outFile = new File(folder, filename + "tif")
			logService.info("Now saving to $outFile.")
			IJ.saveAsTiff(imp, outFile.getAbsolutePath())
		break
		case "ics/ids":
			outFile = new File(folder, filename + "ids")
			logService.info("Now saving to $outFile.")
			IJ.run(imp, "Bio-Formats Exporter", "save=[" + outFile.getAbsolutePath() + "]")
		break
		case "ims":
		default:
			logService.error("Saving to IMS not yet implemented.")
		break
	}
}
