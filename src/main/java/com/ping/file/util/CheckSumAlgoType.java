
package com.ping.file.util;

/**
 * 文件摘要算法.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
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