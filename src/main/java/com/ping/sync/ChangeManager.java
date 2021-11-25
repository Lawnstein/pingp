
package com.ping.sync;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ping.file.util.Utils;

/**
 * 修改管理.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
public class ChangeManager {
	private static final Logger logger = LoggerFactory.getLogger(ChangeManager.class);

	protected static String DEFAULT_FILE_ENCODING = Utils.DEFAULT_FILE_ENCODING;

	public static String basePath = null;

	public static void setBasePath(String path) {
		if (path != null) {
			ChangeManager.basePath = path + File.separator + ".changelog";
			logger.debug("change base path {}", basePath);
		}
	}

	public static String getBasePath() {
		return ChangeManager.basePath;
	}

	private static String getEncodeName(String filepath) {
		File f = new File(filepath);
		String fullpath = null;
		try {
			fullpath = f.getCanonicalPath();
		} catch (IOException e) {
			fullpath = filepath;
		}
		return Utils.getEncode(fullpath);
	}

	public static String readChksum(String sumPath) {
		File cnf = new File(sumPath);
		if (!cnf.exists()) {
			return null;
		}

		FileInputStream is = null;
		String content = null;
		try {
			is = new FileInputStream(cnf);
			int sz = is.available();
			if (sz <= 0)
				return null;

			byte[] contentBytes = new byte[sz];
			int rz = is.read(contentBytes, 0, sz);
			if (rz < sz)
				logger.warn("readFileBytes read " + rz + "/" + sz + ", not complete.");
			return new String(contentBytes);
		} catch (IOException e) {
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
		return null;
	}

	public void writeChksum(String path, String content) throws IOException {
		File cnf = new File(path);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), DEFAULT_FILE_ENCODING));
		writer.write(content);
		writer.close();
		writer = null;
	}

	/**
	 * @param path
	 * @return 0 - no change. <br>
	 *         1 - no sync config file.<br>
	 *         2 - changed.<br>
	 */
	public static int getChanged(String path) {
		String chksumPath = getBasePath() + File.separator + getEncodeName(path);
		String confChksum = readChksum(chksumPath);
		if (confChksum == null || confChksum.length() == 0) {
			return 1;
		}
		String fileChksum = Utils.chksum(path);
		if (confChksum.equals(fileChksum)) {
			return 0;
		}
		return 2;
	}

	/**
	 * @param path
	 * @return 0 - no change. <br>
	 *         1 - no sync config file but not changed.<br>
	 *         2 - changed.<br>
	 */
	public static int getChanged(String path, String chksum) {
		String chksumPath = getBasePath() + File.separator + getEncodeName(path);
		String confChksum = readChksum(chksumPath);
		int stat = -1;
		if (confChksum == null || confChksum.length() == 0) {
			stat = 1;
			String fileChksum = Utils.chksum(path);
			if (!fileChksum.equals(chksum)) {
				stat = 2;
			}
		} else {
			if (confChksum.equals(chksum)) {
				stat = 0;
			} else {
				stat = 2;
			}
		}
		return stat;
	}

	public static void writeChange(String path, String chksum) {
		String fileChksum = chksum;
		if (fileChksum == null || fileChksum.length() == 0) {
			fileChksum = Utils.chksum(path);
		}

		String chksumPath = getBasePath() + File.separator + getEncodeName(path);
		if (!Utils.mkdirsForFile(chksumPath)) {
			if (!Utils.mkdirsForFile(chksumPath)) {
				logger.error("create dir for chksum of {}", path);
				return;
			}
		}

		BufferedWriter writer = null;
		try {
			File cnf = new File(chksumPath);
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), DEFAULT_FILE_ENCODING));
			writer.write(fileChksum);
		} catch (IOException e) {
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
			writer = null;
		}
		logger.debug("file {} chksum wrote.", path);
	}

}
