/**
 * Copyright 2005-2021 Client Service International, Inc. All rights reserved. <br>
 * CSII PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.<br>
 * <br>
 * project: pingp <br>
 * create: 2021-11-4 15:50:07 <br>
 * vc: $Id: $
 */

package com.ping.file.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * TODO 请填写注释.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
public class Utils {
	public final static String DEFAULT_CHECKSUM_ALGO = CheckSumAlgoType.SHA_512.getName();
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

}
