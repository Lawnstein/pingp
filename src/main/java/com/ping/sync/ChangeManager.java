
package com.ping.sync;

import com.ping.file.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

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

    public static void setBasePath(String filePath, String changelogPath) {
        logger.debug("change base path {}, changelogPath {}", filePath, changelogPath);
        String canonicalFilePath = Utils.getCanonicalPath(filePath);
        String canonicalChangelogPath = changelogPath == null ? canonicalFilePath : Utils.getCanonicalPath(changelogPath);
        ChangeManager.basePath = canonicalFilePath + File.separator;
        ChangeManager.changePath = canonicalFilePath
                .equals(canonicalChangelogPath) ? canonicalFilePath + DEFAULT_CHANGLOG_DIR + File.separator : canonicalChangelogPath + File.separator + DEFAULT_CHANGLOG_DIR + File.separator;
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

    private static String getChangelogPath(String path) {
        String fullPath = Utils.getCanonicalPath(path);
        return fullPath.substring(getChangelogDir().length());
    }

    /**
     * @param path
     * @return 0 - no change. <br>
     * 2 - changed.<br>
     * 3 - no sync config file but changed.<br>
     */
    public static int getClientChanged(String path) {
        if (path == null) {
            return 2;
        }

        String changelogPath = getChangelogDir() + getEncodeName(Utils.getCanonicalPath(path));
        String confChksum = readChksum(changelogPath);
        if (confChksum == null || confChksum.length() == 0) {
            return 3;
        }
        String fileChksum = Utils.chksum(path);
        // if (!fileChksum.equals(confChksum)) {
        if (!Utils.equals(fileChksum, confChksum)) {
            return 2;
        }
        return 0;
    }

    public static void writeClientChangelog(String path, String chksum) {
        String changelogPath = getChangelogDir() + getEncodeName(Utils.getCanonicalPath(path));

        String fileChksum = chksum;
        if (fileChksum == null || fileChksum.length() == 0) {
            fileChksum = Utils.chksum(path);
        }

        Utils.fileWrite(changelogPath, chksum);
        logger.debug("file {} chksum wrote.", path);
    }

    public static void deleteClientChanged(String path) {
        String changelogPath = getChangelogDir() + getEncodeName(Utils.getCanonicalPath(path));
        Utils.fileDelete(changelogPath);
        logger.debug("file {} chksum delete.", path);
    }

    /**
     * @param path
     * @param chksum
     * @return status. <br>
     * 0 - no change. <br>
     * 1 - no sync config file but no changed.<br>
     * 2 - changed.<br>
     * 3 - no sync config file but changed.<br>
     */
    public static int getServChangedWithUp(String path, String chksum) {
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
            // if (!chksum.equals(fileChksum)) {
            if (!Utils.equals(chksum, fileChksum)) {
                stat = 3;
            }
        } else {
            // if (chksum.equals(confChksum)) {
            if (Utils.equals(chksum, confChksum)) {
                stat = 0;
            } else {
                stat = 2;
            }
        }
        return stat;
    }

    /**
     * @param path
     * @param chksum
     * @return array [ stat, chksum ] <br>
     * 0 - no change. <br>
     * 1 - no sync config file but no changed.<br>
     * 2 - changed.<br>
     * 3 - no sync config file but changed.<br>
     */
    public static Object[] getServChangedWithDwn(String path, String chksum) {
        if (path == null) {
            return new Object[]{2, null};
        }

        String changelogPath = Utils.getCanonicalPath(getBaseDir() + path);
        changelogPath = Utils.substring(changelogPath, getBaseDir().length());
        changelogPath = getChangelogDir() + getEncodeName(changelogPath);
        String confChksum = readChksum(changelogPath);

        int stat = -1;
        String fileChksum = null;
        if (confChksum == null || confChksum.length() == 0) {
            stat = 1;
            fileChksum = Utils.chksum(getBaseDir() + path);
            // if (!chksum.equals(fileChksum)) {
            if (!Utils.equals(chksum, fileChksum)) {
                stat = 3;
            }
        } else {
            fileChksum = confChksum;
            // if (chksum.equals(fileChksum)) {
            if (Utils.equals(chksum, fileChksum)) {
                stat = 0;
            } else {
                stat = 2;
            }
        }
        return new Object[]{stat, fileChksum};
    }

    public static void writeServChangelog(String path, String chksum) {
        String changelogPath = Utils.getCanonicalPath(getBaseDir() + path);
        changelogPath = Utils.substring(changelogPath, getBaseDir().length());
        changelogPath = getChangelogDir() + getEncodeName(changelogPath);

        String fileChksum = chksum;
        if (fileChksum == null || fileChksum.length() == 0) {
            fileChksum = Utils.chksum(getBaseDir() + path);
        }

        Utils.fileWrite(changelogPath, chksum);
        logger.debug("file {} chksum wrote.", path);
    }

    public static void deleteServChangelog(String path) {
        String changelogPath = Utils.getCanonicalPath(getBaseDir() + path);
        changelogPath = Utils.substring(changelogPath, getBaseDir().length());
        changelogPath = getChangelogDir() + getEncodeName(changelogPath);
        Utils.fileDelete(changelogPath);
        logger.debug("file {} chksum delete.", path);
    }


}
