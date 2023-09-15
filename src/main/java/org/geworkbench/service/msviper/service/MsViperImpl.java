package org.geworkbench.service.msviper.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
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
	private static final String logExt = ".log"; // msviper log file
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
		logger.error(expFname);
		return dataDir;
	}

	/* TODO: After totally testing, signature file maybe need to be removed */
	public MsViperOutput execute(MsViperInput input, String dataDir)
			throws IOException {

		StringBuilder log = new StringBuilder();
		MsViperOutput output = new MsViperOutput();
		String name = input.getDatasetName();
		String runid = new File(dataDir).getName();
		// String prefix = name.substring(0, name.lastIndexOf("."));
		String logfname = name + logExt;
		String shadowValue = input.getShadowValue();
		Float sValue = 25f;
		if (shadowValue != null) {
			sValue = Float.valueOf(shadowValue);
		}

		if (dataDir == null) {
			log.append("Cannot find data dir to store viper input");
			output.setLog(log.toString());
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
		pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(logfname)));
		Process p = pb.start();
		try {
			p.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
			String msg = "Viper job " + runid + " submission error\n";
			logger.error(msg);
			log.append(msg);
			output.setLog(log.toString());
			return output;
		}

		logger.info("Sending msviper output " + name);

		String resultFname = dataDir + resultFileName;
		File resultFile = new File(resultFname);
		String ledgesFname = dataDir + ledgesFileName;
		File ledgeFile = new File(ledgesFname);
		String signatureFname = dataDir + signatureFileName;
		// actually signatureFile is not used in client side, But I would like
		// to keep here in case it
		// will be needed.
		File signatureFile = new File(signatureFname);
		String mrsSignatureFname = dataDir + mrsSignatureFileName;
		File mrsSignatureFile = new File(mrsSignatureFname);
		String mrsFname = dataDir + mrsFileName;
		File mrsFile = new File(mrsFname);
		String regulonsFname = dataDir + regulonsFileName;
		File regulonsFile = new File(regulonsFname);

		if (!resultFile.exists() || !mrsSignatureFile.exists()
				|| !mrsFile.exists() || !regulonsFile.exists()
				|| !ledgeFile.exists()) {
			String err = null;
			if ((err = runError(logfname)) != null) {
				String msg = "MsViper job " + runid + " abnormal termination\n"
						+ err;
				logger.error(msg);
				log.append(msg);
			} else {
				String msg = "MsViper job " + runid + " was killed";
				logger.error(msg);
				log.append(msg);
			}
			output.setLog(log.toString());
			return output;
		}

		output.setLog(log.toString());
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

	private String runError(String logfname) {
		StringBuilder str = new StringBuilder();
		BufferedReader br = null;
		boolean error = false;
		File logFile = new File(logfname);
		if (!logFile.exists())
			return null;
		try {
			br = new BufferedReader(new FileReader(logFile));
			String line = null;
			int i = 0;
			while ((line = br.readLine()) != null) {
				if (((i = line.indexOf("Error")) > -1)) {
					str.append(line.substring(i) + "\n");
					error = true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (error)
			return str.toString();
		return null;
	}
}
