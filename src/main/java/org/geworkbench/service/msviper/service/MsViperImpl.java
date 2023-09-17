package org.geworkbench.service.msviper.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geworkbench.service.msviper.schema.MsViperInput;
import org.geworkbench.service.msviper.schema.MsViperOutput;

public class MsViperImpl implements MsViper {

	private static final Log logger = LogFactory.getLog(MsViperImpl.class);

	private static final Properties properties = new Properties();
	static {
		try {
			InputStream reader = MsViperImpl.class.getResourceAsStream("/application.properties");
			properties.load(reader);
			reader.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	private static final String VIPER_ROOT = properties.getProperty("viper.root");
	private static final String VIPER_RUNS = VIPER_ROOT + "/runs/";
	private static final String scriptDir = VIPER_ROOT + "/scripts/";
	private static final String rscript = properties.getProperty("r.installation") + "/bin/Rscript";
	private static final String PHENOTYPE_FILE = "phenotypes.txt";
	private static final String viperR = "msviper_starter.r";
	private static final String serverRLibPath = VIPER_ROOT + "/R/hpc";
	private static final String resultFileName = "result.txt";
	private static final String ledgesFileName = "ledges.txt";
	private static final String signatureFileName = "signature.txt";
	private static final String mrsSignatureFileName = "mrsSignature.txt";
	private static final String mrsFileName = "masterRegulons.txt";
	private static final String regulonsFileName = "regulons.txt";
	private static final String shadowResultFileName = "shadowResult.txt";
	private static final String shadowPairFileName = "shadowPair.txt";
	private static final Random random = new Random();

	public String storeMsViperInput(MsViperInput input) throws IOException {
		String dataDir = getDataDir();
		if (dataDir == null) {
			logger.error("Cannot find data dir to store msviper input");
			return null;
		}

		String expFname = dataDir + input.getDatasetName();
		String adjFname = dataDir + input.getNetworkFileName();
		String phenoFname = dataDir + PHENOTYPE_FILE;

		writeFile(input.getExpFile(), expFname);
		writeFile(input.getAdjFile(), adjFname);
		writeFile(input.getPhenotypesFile(), phenoFname);
		return dataDir;
	}

	/* TODO: After totally testing, signature file maybe need to be removed */
	public MsViperOutput execute(MsViperInput input, String dataDir)
			throws IOException {

		MsViperOutput output = new MsViperOutput();
		String name = input.getDatasetName();
		String runid = new File(dataDir).getName();
		String shadowValue = input.getShadowValue();
		Float sValue = 25f;
		if (shadowValue != null) {
			sValue = Float.valueOf(shadowValue);
		}

		if (dataDir == null) {
			output.setLog("Cannot find data dir to store viper input");
			return output;
		}

		List<String> submitStr = new ArrayList<String>(Arrays.asList(rscript, scriptDir + viperR, dataDir,
				input.getDatasetName(),
				input.getNetworkFileName(), PHENOTYPE_FILE,
				input.getContext(), input.getCaseGroups(),
				input.getControlGroups(), input.getGesFilter(),
				input.getMinAllowedRegulonSize(),
				input.getBootstrapping(), input.getMethod(),
				input.getShadow()));
		if (sValue > 1) {
			submitStr.add(Integer.toString(sValue.intValue()));
		} else {
			submitStr.add(sValue.toString());
		}
		submitStr.add(serverRLibPath);

		ProcessBuilder pb = new ProcessBuilder(submitStr);
		File logfile = new File(dataDir + name + ".log");
		pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logfile));
		pb.redirectError(ProcessBuilder.Redirect.appendTo(logfile));
		Process p = pb.start();
		try {
			p.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
			String msg = "Viper job " + runid + " submission error\n";
			logger.error(msg);
			output.setLog(msg);
			return output;
		}

		logger.info("Sending msviper output " + name);

		File resultFile = new File(dataDir + resultFileName);
		File ledgeFile = new File(dataDir + ledgesFileName);
		// actually signatureFile is not used in client side, But I would like
		// to keep here in case it
		// will be needed.
		File signatureFile = new File(dataDir + signatureFileName);
		File mrsSignatureFile = new File(dataDir + mrsSignatureFileName);
		File mrsFile = new File(dataDir + mrsFileName);
		File regulonsFile = new File(dataDir + regulonsFileName);

		if (!resultFile.exists() || !mrsSignatureFile.exists()
				|| !mrsFile.exists() || !regulonsFile.exists()
				|| !ledgeFile.exists()) {
			StringBuffer msg = new StringBuffer("MsViper job " + runid + " failed:\n");
			String no = " does not exist\n";
			if (!resultFile.exists()) {
				msg.append("  ").append(resultFileName).append(no);
			}
			if (!mrsSignatureFile.exists()) {
				msg.append("  ").append(signatureFileName).append(no);
			}
			if (!mrsFile.exists()) {
				msg.append("  ").append(mrsFileName).append(no);
			}
			if (!regulonsFile.exists()) {
				msg.append("  ").append(regulonsFileName).append(no);
			}
			if (!ledgeFile.exists()) {
				msg.append("  ").append(ledgesFileName).append(no);
			}
			logger.error(msg);
			output.setLog(msg.toString());
			return output;
		}

		output.setLog(""); // succeeded
		output.setResultName(runid);
		output.setResultFile(new DataHandler(new FileDataSource(resultFile)));
		output.setLedgesFile(new DataHandler(new FileDataSource(ledgeFile)));
		output.setSignatureFile(new DataHandler(new FileDataSource(
				signatureFile)));
		output.setMrsSignatureFile(new DataHandler(new FileDataSource(
				mrsSignatureFile)));
		output.setMrsFile(new DataHandler(new FileDataSource(mrsFile)));
		output.setRegulonsFile(new DataHandler(new FileDataSource(regulonsFile)));

		if (input.getShadow().equals("TRUE")) {
			String shadowResultFname = dataDir + shadowResultFileName;
			File shadowResultFile = new File(shadowResultFname);
			String shadowPairFname = dataDir + shadowPairFileName;
			File shadowPairFile = new File(shadowPairFname);

			output.setShadowResultFile(new DataHandler(new FileDataSource(
					shadowResultFile)));
			output.setShadowPairFile(new DataHandler(new FileDataSource(
					shadowPairFile)));
		}

		logger.info("Sending MsViper output. ");
		return output;
	}

	private String getDataDir() {
		File root = new File(VIPER_RUNS);
		if (!root.exists() && !root.mkdir())
			return null;

		int i = 0;
		String dirname = null;
		File randdir = null;
		try {
			do {
				dirname = VIPER_RUNS + "msvpr" + random.nextInt(Short.MAX_VALUE)
						+ "/";
				randdir = new File(dirname);
			} while (randdir.exists() && ++i < Short.MAX_VALUE);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		if (i < Short.MAX_VALUE) {
			if (!randdir.mkdir())
				return null;
			return dirname;
		} else
			return null;
	}

	public static void writeFile(DataHandler handler, String fname)
			throws RemoteException {
		if (handler == null)
			return;
		File expfile = new File(fname);
		OutputStream os = null;
		try {
			os = new FileOutputStream(expfile);
			handler.writeTo(os);
		} catch (IOException ie) {
			ie.printStackTrace();
			throw new RemoteException("msviper write file Exception: write"
					+ fname, ie);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("msviper write file Exception: write" + fname, e);
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}
