package org.geworkbench.service.msviper.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.Random;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geworkbench.service.msviper.schema.MsViperInput;
import org.geworkbench.service.msviper.schema.MsViperOutput;

public class MsViperImpl implements MsViper {

	private static final Log logger = LogFactory.getLog(MsViperImpl.class);

	private static final String maxmem = "4G";
	private static final String timeout = "48::";
	private String submitBase = "#!/bin/bash\n#$ -l mem=" + maxmem + ",time="
			+ timeout + " -cwd -j y -o ";

	private static final String VIPERROOT = "/ifs/data/c2b2/af_lab/cagrid/r/msviper/runs/";
	private static final String scriptDir = "/ifs/data/c2b2/af_lab/cagrid/r/msviper/scripts/";
	private static final String rscript = "/nfs/apps/R/3.1.2/bin/Rscript";
	private static final String PHENOTYPE_FILE = "phenotypes.txt";
	private static final String account = "cagrid";
	private static final String submitSh = "msviper_submit.sh";
	private static final String viperR = "msviper_starter.r";
	private static final String logExt = ".log"; // msviper log  file
	private static final String serverRLibPath = "/ifs/data/c2b2/af_lab/cagrid/r/msviper/R/hpc";
	private static final String resultFileName = "result.txt";
	private static final String ledgesFileName = "ledges.txt";
	private static final String signatureFileName = "signature.txt";
	private static final String mrsSignatureFileName = "mrsSignature.txt";
	private static final String mrsFileName = "masterRegulons.txt";
	private static final String regulonsFileName = "regulons.txt";
	private static final String shadowResultFileName = "shadowResult.txt";
	private static final String shadowPairFileName = "shadowPair.txt";
	private static final long POLL_INTERVAL = 20000; // 20 seconds
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

	/* TODO: After totally testing, signature file maybe need to be removed  */
	public MsViperOutput execute(MsViperInput input, String dataDir)
			throws IOException {

		StringBuilder log = new StringBuilder();
		MsViperOutput output = new MsViperOutput();
		String name = input.getDatasetName();
		String runid = new File(dataDir).getName();
		String prefix = name.substring(0, name.lastIndexOf("."));
		String logfname = prefix + logExt;
		String shadowValue = input.getShadowValue();
		Float sValue = 25f;
		if (shadowValue != null) {
			sValue = new Float(shadowValue);
		}

		if (dataDir == null) {
			log.append("Cannot find data dir to store viper input");
			output.setLog(log.toString());
			return output;
		}

		String submitStr = submitBase + dataDir + logfname + " -N " + runid
				+ "\n" + rscript + " " + scriptDir + viperR + " " + dataDir
				+ " " + input.getDatasetName() + " "
				+ input.getNetworkFileName() + " " + PHENOTYPE_FILE + " "
				+ input.getContext() + " " + input.getCaseGroups() + " "
				+ input.getControlGroups() + " " + input.getGesFilter() + " "
				+ input.getMinAllowedRegulonSize() + " "
				+ input.getBootstrapping() + " " + input.getMethod() + " "
				+ input.getShadow();
		if (sValue > 1)
			submitStr = submitStr + " " + sValue.intValue() + " "
					+ serverRLibPath;
		else
			submitStr = submitStr + " " + sValue + " " + serverRLibPath;

		String submitFile = dataDir + submitSh;
		if (!writeToFile(submitFile, submitStr)) {
			String msg = "Cannot find write viper job submit script";
			logger.error(msg);
			log.append(msg);
			output.setLog(log.toString());
			return output;
		}

		int ret = submitJob(submitFile);
		if (ret != 0) {
			String msg = "Viper job " + runid + " submission error\n";
			logger.error(msg);
			log.append(msg);
			output.setLog(log.toString());
			return output;
		}

		while (!isJobDone(runid)) {
			try {
				Thread.sleep(POLL_INTERVAL);
			} catch (InterruptedException e) {
			}
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
		File root = new File(VIPERROOT);
		if (!root.exists() && !root.mkdir())
			return null;

		int i = 0;
		String dirname = null;
		File randdir = null;
		try {
			do {
				dirname = VIPERROOT + "msvpr" + random.nextInt(Short.MAX_VALUE)
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

	private boolean writeToFile(String fname, String string) {
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(fname));
			bw.write(string);
			bw.flush();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				if (bw != null)
					bw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return true;
	}

	private int submitJob(java.lang.String jobfile) {
		String command = "qsub " + jobfile;
		System.out.println(command);
		try {
			Process p = Runtime.getRuntime().exec(command);
			StreamGobbler out = new StreamGobbler(p.getInputStream(), "INPUT");
			StreamGobbler err = new StreamGobbler(p.getErrorStream(), "ERROR");
			out.start();
			err.start();
			return p.waitFor();
		} catch (Exception e) {
			return -1;
		}
	}

	private boolean isJobDone(String runid) {
		String cmd = "qstat -u " + account;
		BufferedReader brIn = null;
		BufferedReader brErr = null;
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			brIn = new BufferedReader(new InputStreamReader(p.getInputStream()));
			brErr = new BufferedReader(
					new InputStreamReader(p.getErrorStream()));
			String line = null;
			while ((line = brIn.readLine()) != null
					|| (line = brErr.readLine()) != null) {
				if (line.startsWith("error"))
					return false; // cluster scheduler error
				String[] toks = line.trim().split("\\s+");
				if (toks.length > 3 && toks[2].equals(runid))
					return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return true;
		} finally {
			try {
				if (brIn != null)
					brIn.close();
				if (brErr != null)
					brErr.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return true;
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

	public static class StreamGobbler extends Thread {
		private InputStream is;
		private String type;
		private OutputStream os;

		StreamGobbler(InputStream is, String type) {
			this(is, type, null);
		}

		StreamGobbler(InputStream is, String type, OutputStream redirect) {
			this.is = is;
			this.type = type;
			this.os = redirect;
		}

		public void run() {
			PrintWriter pw = null;
			BufferedReader br = null;
			try {
				if (os != null)
					pw = new PrintWriter(os, true);

				InputStreamReader isr = new InputStreamReader(is);
				br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null) {
					if (pw != null) {
						pw.println(line);
					}
					System.out.println(type + ">" + line);
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			} finally {
				try {
					if (pw != null)
						pw.close();
					if (br != null)
						br.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}
