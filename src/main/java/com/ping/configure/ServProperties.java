/**
 * Copyright 2005-2021 Client Service International, Inc. All rights reserved. <br>
 * CSII PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.<br>
 * <br>
 * project: pingp <br>
 * create: 2021-11-4 17:16:30 <br>
 * vc: $Id: $
 */

package com.ping.configure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
@ConfigurationProperties(prefix = "server", ignoreUnknownFields = false)
public class ServProperties {

	public int port = 0;
	public String dir = null;
	public long timeout = 30;
	public long maxThreads = Runtime.getRuntime().availableProcessors() * 10;

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getDir() {
		return dir;
	}

	public void setDir(String dir) {
		this.dir = dir;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public long getMaxThreads() {
		return maxThreads;
	}

	public void setMaxThreads(long maxThreads) {
		this.maxThreads = maxThreads;
	}

}
