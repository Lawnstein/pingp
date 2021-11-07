/**
 * Copyright 2005-2021 Client Service International, Inc. All rights reserved. <br>
 * CSII PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.<br>
 * <br>
 * project: pingp <br>
 * create: 2021-11-4 15:54:03 <br>
 * vc: $Id: $
 */

package com.ping.file.util;

public enum CheckSumAlgoType {
MD5("MD5"),
SHA_256("SHA-256"),
SHA_512("SHA-512"),
SHA_1("SHA1");

private String name;

private CheckSumAlgoType(String name) {
	this.name = name;

}

public String getName() {
	return name;
}

public void setName(String name) {
	this.name = name;
}

}