
package com.ping.file.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

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
    public final static String DEFAULT_TRANSFERING_CNF_SUFFIX = ".@{cnf}";
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
            return new String[]{str};
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

    public static boolean isEmpty(String source) {
        return source == null || source.length() == 0 ? true : false;
    }

    public static void fileWrite(String path, String content) {
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

    public static boolean fileDelete(String path) {
        return fileDelete(new File(path));
    }

    public static boolean fileDelete(File f) {
        if (f == null || !f.exists()) {
            return false;
        }
        if (f.isFile()) {
            return f.delete();
        } else if (f.isDirectory()) {
            File[] files = f.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (!fileDelete(files[i])) {
                    return false;
                }
            }
            return f.delete();
        }
        return false;
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

    public static String getFirstDirname(String path) {
        if (path == null || path.length() == 0)
            return null;
        String[] names = path.split("[\\\\/]");
        if (names == null || names.length < 1)
            return null;
        return names[0];
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
        File d = new File(".");
        try {
            String dn = d.getCanonicalPath();
            if (!dn.endsWith(File.separator)) {
                dn += File.separator;
            }
            return dn;
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
        getFiles(path, false, list);
    }

    public static void getFiles(String path, boolean includeDir, List<String> list) {
        getFiles(new File(path), includeDir, list);
    }

    public static void getFiles(File file, boolean includeDir, List<String> list) {
        if (file == null) {
            return;
        }
        if (!file.exists()) {
            logger.error("{} not exists or cannot read.", file.getPath());
            return;
        }
        if (file.isDirectory()) {
            if (includeDir) {
                list.add(file.getPath() + File.separator);
            }
            File[] files = file.listFiles();
            if (files != null) {
                for (File sf : files) {
                    getFiles(sf, includeDir, list);
                }
            }
        } else if (file.isFile()) {
            if (!file.getPath().endsWith(Utils.DEFAULT_TRANSFERING_CNF_SUFFIX)) {
                list.add(file.getPath());
            }
        } else {
            logger.warn("Unsupprted file type (not common file or directory) : {}", file.getPath());
        }
    }

    public static boolean isDirectoryPath(String path) {
        return path == null ? false : (path.endsWith("/") || path.endsWith("\\"));
    }

    public static String getFormatedPath(String path) {
        return (new File(path == null ? "" : path)).getPath() + (isDirectoryPath(path) ? File.separator : "");
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
//        File f1 = new File(".\\test1.txt");
//        File f2 = new File("D:\\git\\pingp\\test1.txt");
//        File f3 = new File("git//pingp\\test1.txt");
//        File f4 = new File("git//pingp\\/");
//        System.out.println("abcd".substring("ab".length()));
//        System.out.println(File.separator);
//        System.out.println(getCanonicalPath(null));
//        System.out.println("---------------------------------");
//        System.out.println("f1.getPath()=" + f1.getPath());
//        System.out.println("f1.getAbsolutePath()=" + f1.getAbsolutePath());
//        System.out.println("f1.getCanonicalPath()=" + f1.getCanonicalPath());
//        System.out.println("---------------------------------");
//        System.out.println("f2.getPath()=" + f2.getPath());
//        System.out.println("f2.getAbsolutePath()=" + f2.getAbsolutePath());
//        System.out.println("f2.getCanonicalPath()=" + f2.getCanonicalPath());
//        System.out.println("---------------------------------");
//        System.out.println("f3.getPath()=" + f3.getPath());
//        System.out.println("f3.getAbsolutePath()=" + f3.getAbsolutePath());
//        System.out.println("f3.getCanonicalPath()=" + f3.getCanonicalPath());
//        System.out.println("---------------------------------");
//        System.out.println("f4.getPath()=" + f4.getPath());
//        System.out.println("f4.getAbsolutePath()=" + f4.getAbsolutePath());
//        System.out.println("f4.getCanonicalPath()=" + f4.getCanonicalPath());
//        System.out.println("---------------------------------");
//        String path = "D:\\doc\\20201217马上消费\\提交内网SVN\\svn";
////        String path = "C:\\Users\\lawnstein.chan\\Desktop\\1";
//        List<String> l = new ArrayList<String>();
//        Utils.getFiles(path, true, l);
//        Collections.sort(l);
//        for (String fn : l) {
//            logger.debug(fn);
//        }
    }

}
