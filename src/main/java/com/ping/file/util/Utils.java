
package com.ping.file.util;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件摘要工具类.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
public class Utils {
	private static final Logger logger = LoggerFactory.getLogger(Utils.class);
	public final static String DEFAULT_CHECKSUM_ALGO = CheckSumAlgoType.SHA_512.getName();
	public final static String DEFAULT_FILE_ENCODING = "UTF-8";
	public final static long DEFAULT_CHUNK_SIZE = 1024;
	public final static int HEADLENGTH = 8;
	public final static int DEFAULT_TIMEOUT_SEC = 30;

	public static String chksum(String filename) {
		File file = new File(filename);
		if (file.isDirectory()) {
			return null;
		}
		if (!file.exists()) {
			return null;
		}
		try {
			MessageDigest messageDigest = MessageDigest.getInstance(DEFAULT_CHECKSUM_ALGO);
			messageDigest.update(Files.readAllBytes(file.toPath()));
			byte[] digestBytes = messageDigest.digest();
			StringBuffer sb = new StringBuffer();
			for (byte b : digestBytes) {
				sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException | IOException e) {
			return null;
		}
	}

	private final static Base64.Encoder encoder = Base64.getEncoder();

	private static String getBase64(String name) throws UnsupportedEncodingException {
		return encoder.encodeToString(name.getBytes("UTF-8"));
	}

	private static String getHex(String name) {
		String str = "";
		for (int i = 0; i < name.length(); i++) {
			int ch = (int) name.charAt(i);
			String s4 = Integer.toHexString(ch);
			str = str + s4;
		}
		return str;
	}

	public static String getEncode(String name) {
		try {
			return getBase64(name);
		} catch (Throwable thr) {
			return getHex(name);
		}
	}

	public static boolean exists(String path) {
		return exists(new File(path));
	}

	public static boolean exists(File path) {
		return path.exists();
	}

	public static String getBasename(String path) {
		if (path == null || path.length() == 0)
			return null;
		String[] names = path.split("[\\\\/]");
		if (names == null || names.length == 0)
			return null;
		return names[names.length - 1];
	}

	public static String getDirname(String path) {
		if (path == null || path.length() == 0)
			return null;
		String basename = getBasename(path);
		if (basename == null)
			return null;
		return path.substring(0, path.length() - basename.length());
	}

	public static boolean mkdirsForFile(String absoluteFile) {
		String parent = getDirname(absoluteFile);
		return mkdirs(parent);
	}

	public static boolean mkdirs(String absoluteDirectory) {
		return mkdirs(new File(absoluteDirectory));
	}

	public static boolean mkdirs(File dir) {
		if (dir == null)
			return false;
		if (dir.exists()) {
			if (!dir.isDirectory()) {
				logger.error("The directory " + dir.getAbsolutePath() + " exist and not a directory, cannot create dir.");
				return false;
			} else {
				return true;
			}
		}
		boolean result = dir.mkdirs();
		logger.debug("The directory {} not exist, mkdirs {}", dir.getAbsolutePath(), result);
		return result;
	}

	public static boolean createNewFile(String absoluteFile) {
		if (!mkdirsForFile(absoluteFile)) {
			logger.error("Create directory for file " + absoluteFile + " failed.");
			return false;
		}
		try {
			return new File(absoluteFile).createNewFile();
		} catch (IOException e) {
			logger.error("createNewFile for " + absoluteFile + " IOException:", e);
		}
		return false;
	}

}
