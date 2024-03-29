
package com.ping.configure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * client端配置.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
@Component
@Configuration
@ConfigurationProperties(prefix = "client", ignoreUnknownFields = false)
public class ClientProperties {

	public String ip = null;
	public int port = 0;
	public long retry = 0;
	public long timeout = 0;
	public long maxThreads = Runtime.getRuntime().availableProcessors() * 4;
	public long chunkSize = 0;
	public boolean sync = true;
	public boolean debug = false;
	public boolean cvsExclude = true;

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public long getRetry() {
		return retry;
	}

	public void setRetry(long retry) {
		this.retry = retry;
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

	public long getChunkSize() {
		return chunkSize;
	}

	public void setChunkSize(long chunkSize) {
		this.chunkSize = chunkSize;
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

	public boolean isCvsExclude() {
		return cvsExclude;
	}

	public void setCvsExclude(boolean cvsExclude) {
		this.cvsExclude = cvsExclude;
	}

}
