
package com.ping.file.serv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ping.configure.ServProperties;
import com.ping.file.protocol.Command;
import com.ping.file.protocol.Packet;
import com.ping.file.util.ClientSocket;
import com.ping.file.util.NamedThreadFactory;
import com.ping.file.util.Utils;
import com.ping.sync.ChangeManager;

/**
 * TCP服务.
 *
 * @author Lawnstein.Chan
 * @version $Revision:$
 */
public class TcpServer {
	private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

	protected static String DEFAULT_FILE_ENCODING = Utils.DEFAULT_FILE_ENCODING;
	protected final int HEADLENGTH = Utils.HEADLENGTH;
	protected final int EXPIRED_MILLIS = 30 * 1000;

	private TcpServer instance = null;

	/**
	 * TCP监听端口号.
	 */
	protected int port = 40001;

	protected String dir = null;

	protected Thread acceptThread = null;

	/**
	 * 读取超时时间
	 */
	protected int timeout = Utils.DEFAULT_TIMEOUT_SEC;

	/**
	 * TCP请求处理并发处理线程.
	 */
	protected int maxHandleThreads = Runtime.getRuntime().availableProcessors() * 10;

	protected boolean sync = true;

	/**
	 * TCP请求处理并发处理线程池.
	 */
	private ExecutorService handlePool = null;

	private ServerSocket server = null;

	private volatile boolean alived = false;

	/**
	 * @param port
	 * @param dir
	 */
	public TcpServer(int port, String dir, int maxThreads, ServProperties propties) {
		super();

		String dirStr = System.getProperty("server.dir");
		if (dirStr != null && dirStr.length() > 0) {
			this.dir = dirStr;
		} else {
			this.dir = dir != null && dir.length() > 0 ? dir : propties.dir;
		}

		String portStr = System.getProperty("server.port");
		if (portStr != null && portStr.length() > 0) {
			this.port = Integer.valueOf(portStr);
		} else {
			this.port = port > 0 ? port : propties.port;
		}

		String maxHandleThreadsStr = System.getProperty("server.max-threads");
		if (maxHandleThreadsStr != null && maxHandleThreadsStr.length() > 0) {
			this.maxHandleThreads = Integer.valueOf(maxHandleThreadsStr);
		} else if (maxThreads > 0) {
			this.maxHandleThreads = maxThreads;
		} else if (propties != null && propties.maxThreads > 0) {
			this.maxHandleThreads = (int) propties.maxThreads;
		}

		String syncStr = System.getProperty("client.sync");
		if (syncStr != null && syncStr.length() > 0) {
			this.sync = Boolean.valueOf(syncStr);
		} else if (propties != null) {
			this.sync = propties.sync;
		}

		ChangeManager.setBasePath(this.dir, null);
		instance = this;
	}

	public void start() {
		if (handlePool != null) {
			handlePool.shutdown();
			handlePool = null;
		}
		if (server != null) {
			try {
				server.close();
			} catch (IOException e) {
			}
			server = null;
		}

		alived = true;

		server = createServerSocket(port);
		handlePool = Executors.newFixedThreadPool(maxHandleThreads, new NamedThreadFactory("Handler"));
		Runnable acceptRunnable = new Runnable() {
			@Override
			public void run() {
				logger.info("TcpServer on port {} started, with {} handlers. ", port, maxHandleThreads);
				while (alived) {
					try {
						Socket s = server.accept();
						if (s != null) {
							logger.debug("Accepted connection {}", s);
							s.setTcpNoDelay(true);
							s.setSoLinger(true, 10);
							handlePool.execute(new Filter(instance, s));
						}
					} catch (Throwable thr) {
						if (alived) {
							logger.warn("TcpServer accept on port [{}] failed.", port, thr);
							/**
							 * restart if server was closed.
							 */
							if (!server.isBound() && server.isClosed()) {
								closeServerSocket(server);
								RuntimeException cr = null;
								for (int i = 0; i < 3; i++) {
									cr = null;
									try {
										server = createServerSocket(port);
									} catch (RuntimeException thr1) {
										cr = thr1;
										try {
											Thread.sleep(3000);
										} catch (InterruptedException e) {
										}
									}
								}
								if (cr != null) {
									throw cr;
								}
								logger.info("TcpServer on port {} restarted, with {} handlers.", port, maxHandleThreads);
							}
						}
					}
				}
			}
		};
		// acceptThread = new Thread(acceptRunnable);
		// acceptThread.setDaemon(true);
		// acceptThread.start();
		acceptRunnable.run();

	}

	public void stop() {
		alived = false;
		if (acceptThread != null) {
			acceptThread.interrupt();
			acceptThread = null;
		}
		if (server != null) {
			try {
				server.close();
			} catch (IOException e) {
			}
			server = null;
		}
		if (handlePool != null) {
			handlePool.shutdown();
			handlePool = null;
		}
		logger.warn("TcpServer was stopped.");
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public int getMaxHandleThreads() {
		return maxHandleThreads;
	}

	public void setMaxHandleThreads(int maxHandleThreads) {
		this.maxHandleThreads = maxHandleThreads;
	}

	public boolean isSync() {
		return sync;
	}

	private ServerSocket createServerSocket(int port) {
		ServerSocket s;
		try {
			s = new ServerSocket();
			s.setReuseAddress(true);
			s.bind(new InetSocketAddress(port));
			return s;
		} catch (Throwable e) {
			logger.error("Create TcpServer on port {} IOException.", port, e);
			throw new RuntimeException("Create TcpServer bind on port " + port + " failed.", e);
		}
	}

	private void closeServerSocket(ServerSocket s) {
		if (s == null) {
			return;
		}
		try {
			s.close();
		} catch (IOException e) {
		}
	}

}
