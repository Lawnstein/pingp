
package com.ping.file.util;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

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
	private final static Base64.Decoder decoder = Base64.getDecoder();

	public static String encodeBase64(String name) {
		try {
			return encoder.encodeToString(name.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("encode with base64 failed, " + e.getMessage(), e);
		}
	}

	public static String decodeBase64(String str) {
		try {
			return new String(decoder.decode(str), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("decode with base64 failed, " + e.getMessage(), e);
		}
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
			return encodeBase64(name);
		} catch (Throwable thr) {
			return getHex(name);
		}
	}

	public static String[] split(String str, int averageSize) {
		if (str == null || str.length() == 0) {
			return new String[0];
		}
		if (averageSize >= str.length()) {
			return new String[] { str };
		}
		int count = (int) Math.ceil(str.length() / (float) averageSize);
		String[] ret = new String[count];
		for (int i = 0; i < count; i++) {
			if ((i + 1) * averageSize < str.length()) {
				ret[i] = str.substring(i * averageSize, (i + 1) * averageSize);
			} else {
				ret[i] = str.substring(i * averageSize);
			}
		}
		return ret;
	}

	public static boolean fileExists(String path) {
		File f = new File(path);
		if (f.exists() && f.isFile()) {
			return true;
		}
		return false;
	}

	public static boolean dirExists(String path) {
		File f = new File(path);
		if (f.exists() && f.isDirectory()) {
			return true;
		}
		return false;
	}

	public static boolean exists(String path) {
		return exists(new File(path));
	}

	public static boolean exists(File path) {
		return path.exists();
	}

	public static long filesize(String path) {
		File f = new File(path);
		if (!f.exists() || !f.isFile()) {
			return -1;
		}
		return f.length();
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

	public static String getPwd() {
		File directory = new File(".");
		try {
			return directory.getCanonicalPath() + File.separator;
		} catch (IOException e) {
			return null;
		}
	}

	public static boolean mkdirsForFile(String path) {
		String parent = getDirname(getCanonicalPath(path));
		if (!mkdirs(parent)) {
			if (!mkdirs(parent)) {
				return false;
			}
		}
		return true;
	}

	public static boolean mkdirs(String dir) {
		return mkdirs(new File(dir));
	}

	public static boolean mkdirs(File dir) {
		if (dir == null)
			return false;
		if (dir.exists()) {
			if (!dir.isDirectory()) {
				return false;
			} else {
				return true;
			}
		} else {
			return dir.mkdirs();
		}
	}

	// public static boolean createNewFile(String absoluteFile) {
	// if (!mkdirsForFile(absoluteFile)) {
	// logger.error("Create directory for file " + absoluteFile + " failed.");
	// return false;
	// }
	// try {
	// return new File(absoluteFile).createNewFile();
	// } catch (IOException e) {
	// logger.error("createNewFile for " + absoluteFile + " IOException:", e);
	// }
	// return false;
	// }

	public static boolean equals(String o1, String o2) {
		if (o1 == null && o2 == null) {
			return true;
		}
		if (o1 == null || o2 == null) {
			return false;
		}
		return o1.equals(o2);
	}

	public static String substring(String str, int beginIndex) {
		return substring(str, beginIndex, -1);
	}

	public static String substring(String str, int beginIndex, int endIndex) {
		return substring(str, beginIndex, endIndex, null);
	}

	public static String substring(String str, int beginIndex, int endIndex, String nullDefaultValue) {
		if ((str == null || str.length() == 0) && nullDefaultValue != null) {
			return nullDefaultValue;
		}
		int s = str.length();
		if (beginIndex < 0) {
			beginIndex = 0;
		}
		if (endIndex < 0) {
			endIndex = s;
		}
		if (beginIndex > s) {
			beginIndex = s;
		}
		if (endIndex > s) {
			endIndex = s;
		}
		if (beginIndex > endIndex) {
			beginIndex = endIndex;
		}
		String i = str.substring(beginIndex, endIndex);
		if ((i == null || i.length() == 0) && nullDefaultValue != null) {
			return nullDefaultValue;
		} else {
			return i;
		}
	}

	public static void getFiles(String path, List<String> list) {
		File file = new File(path);
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (int i = 0; i < files.length; i++) {
				if (files[i].isDirectory()) {
					getFiles(files[i].getPath(), list);
				} else {
					list.add(files[i].getPath());
				}
			}
		} else if (file.isFile()) {
			list.add(file.getPath());
		} else if (!file.exists()) {
			logger.error("{} not exists or cannot read.", path);
		} else {
			logger.warn("Unsupprted file type (not common file and directory) for {}", path);
		}
	}

	public static String getCanonicalPath(String path) {
		try {
			return (new File(path == null ? "" : path)).getCanonicalPath();
		} catch (IOException e) {
			logger.error("getCanonicalPath {} failed, {}", path, e.getMessage());
			throw new RuntimeException("get canonical path '" + path + "' failed, " + e.getMessage(), e);
		}
	}

	public static void main(String[] args) throws IOException {
		File f1 = new File(".\\test1.txt");
		File f2 = new File("D:\\git\\pingp\\test1.txt");
		System.out.println(File.separator);
		System.out.println(getCanonicalPath(null));
		System.out.println("---------------------------------");
		System.out.println(f1.getPath());
		System.out.println(f1.getAbsolutePath());
		System.out.println(f1.getCanonicalPath());
		System.out.println("---------------------------------");
		System.out.println(f2.getPath());
		System.out.println(f2.getAbsolutePath());
		System.out.println(f2.getCanonicalPath());
		System.out.println("---------------------------------");
	}
}
