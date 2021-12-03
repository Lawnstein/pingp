
package com.ping.configure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * server端配置.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
@Component
@Configuration
@ConfigurationProperties(prefix = "server", ignoreUnknownFields = false)
public class ServProperties {

	public int port = 0;
	public String dir = null;
	public long timeout = 30;
	public long maxThreads = Runtime.getRuntime().availableProcessors() * 10;
	public boolean sync = true;
	public boolean debug = false;

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

	public boolean isSync() {
		return sync;
	}

	public void setSync(boolean sync) {
		this.sync = sync;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

}
