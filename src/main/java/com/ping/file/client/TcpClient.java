
package com.ping.file.client;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ping.configure.ClientProperties;
import com.ping.file.protocol.Command;
import com.ping.file.protocol.Packet;
import com.ping.file.util.ClientSocket;
import com.ping.file.util.NamedThreadFactory;
import com.ping.file.util.Utils;
import com.ping.sync.ChangeManager;

/**
 * 客户端操作.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
public class TcpClient {
	private static final Logger logger = LoggerFactory.getLogger(TcpClient.class);

	protected final int HEADLENGTH = Utils.HEADLENGTH;
	protected final long CHUNKSIZE = Utils.DEFAULT_CHUNK_SIZE;

	protected String ip;
	protected int port;
	protected int timeout = Utils.DEFAULT_TIMEOUT_SEC;
	protected int retry = 30;
	protected boolean sync = true;
	protected String path;
	protected String[] fileList;

	/**
	 * TCP请求处理并发处理线程.
	 */
	protected int maxHandleThreads = Runtime.getRuntime().availableProcessors();

	/**
	 * TCP请求处理并发处理线程池.
	 */
	private ExecutorService handlePool = null;

	/**
	 * 处理进度.
	 */
	private int totalCounter = 0;
	private AtomicInteger handleCounter = new AtomicInteger(0);
	private int perct = -1;

	public TcpClient(String ip, int port, String path, int maxThreads, ClientProperties propties) {
		super();
		this.path = path;
		this.ip = ip != null && ip.length() > 0 ? ip : propties.ip;
		this.port = port > 0 ? port : propties.port;
		if (propties != null && propties.timeout > 0) {
			this.timeout = (int) propties.timeout;
		}

		String retryStr = System.getProperty("client.retry");
		if (retryStr != null && retryStr.length() > 0) {
			this.retry = Integer.valueOf(retryStr);
		} else if (propties != null && propties.retry > 0) {
			this.retry = (int) propties.retry;
		}

		String maxHandleThreadsStr = System.getProperty("client.max-threads");
		if (maxHandleThreadsStr != null && maxHandleThreadsStr.length() > 0) {
			this.maxHandleThreads = Integer.valueOf(maxHandleThreadsStr);
		} else if (maxThreads > 0) {
			this.maxHandleThreads = maxThreads;
		} else if (propties != null && propties.maxThreads > 0) {
			this.maxHandleThreads = (int) propties.maxThreads;
		}
		System.out.println(this.maxHandleThreads);

		String syncStr = System.getProperty("client.sync");
		if (syncStr != null && syncStr.length() > 0) {
			this.sync = Boolean.valueOf(syncStr);
		} else if (propties != null) {
			this.sync = propties.sync;
		}

		ChangeManager.setBasePath(null, System.getProperty("user.home"));
		this.handlePool = Executors.newFixedThreadPool(maxHandleThreads, new NamedThreadFactory("Handler"));
	}

	public boolean isSync() {
		return sync;
	}

	public void startPerct() {
		System.out.print("0");
		if (totalCounter == 0) {
			return;
		}

		Runnable r = new Runnable() {
			@Override
			public void run() {
				while (true) {
					int pct = handleCounter.intValue() * 100 / totalCounter;
					if (pct == perct) {
						return;
					}
					if (perct != -1) {
						String s = perct + "";
						for (int i = 0; i < s.length(); i++) {
							System.out.print("\b \b");
						}
					}
					System.out.print(pct + "");
					if (perct >= 100) {
						break;
					}

					perct = pct;
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
				}
			}

		};
		Thread pt = new Thread(r);
		pt.setDaemon(true);
		pt.start();
	}

	public void download() {
		for (int i = 0; i < retry; i++) {
			try {
				DwlistHandler listHandler = new DwlistHandler(this, path);
				listHandler.run();
				break;
			} catch (Throwable e) {
				logger.error("list " + path + " failed, " + e.getMessage());
			}
		}

		logger.debug("List {} file(s) for {} .", fileList == null ? 0 : fileList.length, path);
		if (fileList == null || fileList.length == 0) {
			return;
		}

		String pwd = Utils.getPwd();
		List<String> ud = new ArrayList<String>();
		totalCounter = fileList.length;
		startPerct();
		CountDownLatch cdl = new CountDownLatch(fileList.length);
		for (String s : fileList) {
			final String fullname = pwd + s;
			final TcpClient owner = this;
			Runnable r = new Runnable() {
				@Override
				public void run() {
					Exception thr = null;
					for (int i = 0; i < retry; i++) {
						try {
							DwfileHandler h = new DwfileHandler(owner, fullname, s);
							h.run();
							thr = null;
							break;
						} catch (Exception e) {
							logger.error("dw {} with {} failed, {}", path, s, e.getMessage(), e);
							thr = e;
						}
					}
					if (thr != null) {
						ud.add(s);
					}
					cdl.countDown();
					handleCounter.getAndIncrement();
				}
			};
			handlePool.execute(r);
		}

		try {
			cdl.await();
			handlePool.shutdown();
		} catch (InterruptedException e) {
		}

		if (logger.isDebugEnabled()) {
			String uds = "";
			for (String s : ud) {
				uds += "\n" + s;
			}
			logger.debug("{} file(s) dw over, {} failed {}", fileList.length, ud.size(), uds);
		}
	}

	public void upload() {
		List<String> l = new ArrayList<String>();
		Utils.getFiles(path, l);
		logger.debug("Locate {} file(s) for {} .", l.size(), path);
		if (l.size() == 0) {
			return;
		}

		File f = new File(path);
		String origDir = "";
		if (f.isFile()) {
			origDir = Utils.getDirname(path);
		} else if (f.isDirectory()) {
			origDir = path.substring(0, path.length() - Utils.getBasename(path).length());
		}
		List<String> ud = new ArrayList<String>();
		totalCounter = l.size();
		startPerct();
		CountDownLatch cdl = new CountDownLatch(l.size());
		for (String s : l) {
			final String fn = origDir == null || origDir.length() == 0 ? s : s.substring(origDir.length() - 1);
			final TcpClient owner = this;
			Runnable r = new Runnable() {
				@Override
				public void run() {
					Exception thr = null;
					for (int i = 0; i < retry; i++) {
						try {
							int stat = -1;
							boolean up = true;
							if (isSync()) {
								stat = ChangeManager.getClientChanged(s);
								if (stat == 0) {
									up = false;
								}
							}
							if (up) {
								UpHandler h = new UpHandler(owner, s, fn);
								h.run();
								if (h.isResult() && stat > 0) {
									ChangeManager.writeClientChangelog(s, h.getFileChksum());
								}
								thr = null;
							}
							break;
						} catch (Exception e) {
							logger.error("up {} with {} failed, {}", path, s, e.getMessage(), e);
							thr = e;
						}
					}
					if (thr != null) {
						ud.add(s);
					}
					cdl.countDown();
					handleCounter.getAndIncrement();
				}
			};
			handlePool.execute(r);
		}

		try {
			cdl.await();
			handlePool.shutdown();
		} catch (InterruptedException e) {
		}

		if (logger.isDebugEnabled()) {
			String uds = "";
			for (String s : ud) {
				uds += "\n" + s;
			}
			logger.debug("{} file(s) up over, {} failed {}", l.size(), ud.size(), uds);
		}
	}

}
