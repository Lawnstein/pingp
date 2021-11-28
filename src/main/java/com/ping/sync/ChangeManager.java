
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

	protected static String DEFAULT_CHANGLOG_DIR = ".changelog";

	protected static String basePath = null;
	protected static String changePath = null;
	// protected static boolean clientMode = false;

	// public static void setBasePath(String filePath, String changelogPath, boolean clientMode) {
	// setBasePath(filePath, changelogPath);
	// ChangeManager.clientMode = clientMode;
	// }

	public static void setBasePath(String filePath, String changelogPath) {
		logger.debug("change base path {}, changelogPath {}", filePath, changelogPath);
		String canonicalFilePath = Utils.getCanonicalPath(filePath);
		String canonicalChangelogPath = changelogPath == null ? canonicalFilePath : Utils.getCanonicalPath(changelogPath);
		ChangeManager.basePath = canonicalFilePath + File.separator;
		ChangeManager.changePath = canonicalChangelogPath + File.separator + DEFAULT_CHANGLOG_DIR + File.separator;
		logger.debug("basePath {}", ChangeManager.basePath);
		logger.debug("changelogPath {}", ChangeManager.changePath);
	}

	public static String getBaseDir() {
		return ChangeManager.basePath;
	}

	public static String getChangelogDir() {
		return ChangeManager.changePath;
	}

	private static String getEncodeName(String filepath) {
		File f = new File(filepath);
		String fullpath = null;
		try {
			fullpath = f.getCanonicalPath();
		} catch (IOException e) {
			fullpath = filepath;
		}
		String ecodingPath = Utils.getEncode(fullpath);
		StringBuffer ecodingBuffer = new StringBuffer(ecodingPath);
		ecodingBuffer.reverse();
		ecodingPath = ecodingBuffer.toString();
		String[] eps = ecodingPath.split("[/]");
		String encodingName = "";
		for (String s : eps) {
			String[] ss = Utils.split(s, 32);
			for (String si : ss) {
				if (encodingName.length() > 0) {
					encodingName += File.separator;
				}
				encodingName += si;
			}
		}
		return encodingName;
	}

	public static String readChksum(String chksumPath) {
		File cnf = new File(chksumPath);
		if (!cnf.exists()) {
			return null;
		}

		FileInputStream is = null;
		// String content = null;
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

	// public void writeChksum(String path, String content) throws IOException {
	// File cnf = new File(path);
	// BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), DEFAULT_FILE_ENCODING));
	// writer.write(content);
	// writer.close();
	// writer = null;
	// }

	private static String getChangelogPath(String path) {
		String fullPath = Utils.getCanonicalPath(path);
		return fullPath.substring(getChangelogDir().length());
	}

	/**
	 * @param path
	 * @return 0 - no change. <br>
	 *         1 - no sync config file.<br>
	 *         2 - changed.<br>
	 */
	public static int getClientChanged(String path) {
		if (path == null) {
			return 2;
		}

		String changelogPath = getChangelogDir() + getEncodeName(Utils.getCanonicalPath(path));
		String confChksum = readChksum(changelogPath);
		if (confChksum == null || confChksum.length() == 0) {
			return 1;
		}
		String fileChksum = Utils.chksum(path);
		if (fileChksum.equals(confChksum)) {
			return 0;
		}
		return 2;
	}

	public static void writeClientChangelog(String path, String chksum) {
		String changelogPath = getChangelogDir() + getEncodeName(Utils.getCanonicalPath(path));

		String fileChksum = chksum;
		if (fileChksum == null || fileChksum.length() == 0) {
			fileChksum = Utils.chksum(path);
		}

		writeContent(changelogPath, chksum);
		logger.debug("file {} chksum wrote.", path);
	}

	/**
	 * @param path
	 * @param chksum
	 * @return 0 - no change. <br>
	 *         1 - no sync config file but not changed.<br>
	 *         2 - changed.<br>
	 */
	public static int getServChanged(String path, String chksum) {
		if (path == null || chksum == null || chksum.length() == 0) {
			return 2;
		}

		String changelogPath = Utils.getCanonicalPath(getBaseDir() + path);
		changelogPath = Utils.substring(changelogPath, getBaseDir().length());
		changelogPath = getChangelogDir() + getEncodeName(changelogPath);
		String confChksum = readChksum(changelogPath);

		int stat = -1;
		if (confChksum == null || confChksum.length() == 0) {
			stat = 1;
			String fileChksum = Utils.chksum(getBaseDir() + path);
			if (!chksum.equals(fileChksum)) {
				stat = 2;
			}
		} else {
			if (chksum.equals(confChksum)) {
				stat = 0;
			} else {
				stat = 2;
			}
		}
		return stat;
	}

	public static void writeServChangelog(String path, String chksum) {
		String changelogPath = Utils.getCanonicalPath(getBaseDir() + path);
		changelogPath = Utils.substring(changelogPath, getBaseDir().length());
		changelogPath = getChangelogDir() + getEncodeName(changelogPath);

		String fileChksum = chksum;
		if (fileChksum == null || fileChksum.length() == 0) {
			fileChksum = Utils.chksum(getBaseDir() + path);
		}

		writeContent(changelogPath, chksum);
		logger.debug("file {} chksum wrote.", path);
	}

	private static void writeContent(String path, String content) {
		if (!Utils.mkdirsForFile(path)) {
			logger.error("create dir for file '{}' failed.", path);
			return;
		}
		BufferedWriter writer = null;
		try {
			File cnf = new File(path);
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), DEFAULT_FILE_ENCODING));
			writer.write(content);
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
	}

	public static void main(String[] args) {
		String a = "2Requirement需求\\\\马上系统接口文档\\\\支付文档\\\\支持的银行卡列，场景码，错误码 .doc";
		String b = "D:\\doc\\20201217马上消费\\提交内网SV\\2Requirement需求\\马上系统接口文档\\支付文档\\支持的银行卡列，场景码，错误码 .doc";

		// try {
		String c = "D:\\doc";
		System.out.println(b.substring(c.length()));

		setBasePath("D:\\\\doc\\\\20201217马上消费\\\\提交内网SV", null);
		System.out.println(getEncodeName(a));

		System.out.println(c.substring(1));
		// File f = new File(a);
		// System.out.println(f.getCanonicalPath());
		//
		// f = new File(b);
		// System.out.println(f.getCanonicalPath());

		// } catch (IOException e) {
		// e.printStackTrace();
		// }

	}
}
