
package com.ping.file.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ping.configure.ClientProperties;
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
	protected final String[] CVS_FOLDERS = { File.separator + ".git" + File.separator, File.separator + ".svn" + File.separator };

	protected String ip;
	protected int port;
	protected int timeout = Utils.DEFAULT_TIMEOUT_SEC;
	protected int retry = 30;
	protected Long chunkSize;
	protected boolean sync = true;
	protected boolean debug = false;
	protected boolean cvsExclude = true;
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

		String chunkSizeStr = System.getProperty("client.chunk-size");
		if (chunkSizeStr != null && chunkSizeStr.length() > 0) {
			this.chunkSize = Long.valueOf(chunkSizeStr);
		} else if (propties != null && propties.chunkSize > 0) {
			this.chunkSize = propties.chunkSize;
		}

		String syncStr = System.getProperty("client.sync");
		if (syncStr != null && syncStr.length() > 0) {
			this.sync = Boolean.valueOf(syncStr);
		} else if (propties != null) {
			this.sync = propties.sync;
		}

		String debugStr = System.getProperty("client.debug");
		if (debugStr != null && debugStr.length() > 0) {
			this.debug = Boolean.valueOf(debugStr);
		} else if (propties != null) {
			this.debug = propties.debug;
		}

		String cvsExcludeStr = System.getProperty("client.cvs-exclude");
		if (cvsExcludeStr != null && cvsExcludeStr.length() > 0) {
			this.cvsExclude = Boolean.valueOf(cvsExcludeStr);
		} else if (propties != null) {
			this.cvsExclude = propties.cvsExclude;
		}

		ChangeManager.setBasePath(null, System.getProperty("user.home"));
		this.handlePool = Executors.newFixedThreadPool(maxHandleThreads, new NamedThreadFactory("Handler"));
	}

	public boolean isSync() {
		return sync;
	}

	public boolean isDebug() {
		return debug;
	}

	public void startPerct(final int totalCounter, final CountDownLatch countDown) {
		if (totalCounter == 0 || countDown == null) {
			return;
		}

		Runnable r = new Runnable() {
			@Override
			public void run() {
				System.out.println("\n\n\n");

				int perct = -1;
				while (perct < 100) {
					int pct = (int) ((totalCounter - countDown.getCount()) * 100 / totalCounter);
					if (pct == perct) {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
						}
						continue;
					}

					perct = pct;
					String s = perct + "";
					System.out.print(s);
					for (int i = 0; i < s.length(); i++) {
						System.out.print("\b");
					}

					try {
						Thread.sleep(500);
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
				ClientDwlistHandler listHandler = new ClientDwlistHandler(this, path);
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
		boolean specifiedSingleFile = false;
		if (fileList.length == 1 && Utils.getBasename(fileList[0]).equals(Utils.getBasename(path))) {
			specifiedSingleFile = true;
		}

		String pwd = Utils.getPwd();
		List<String> ud = new ArrayList<String>();
		CountDownLatch cdl = new CountDownLatch(fileList.length);
		startPerct(fileList.length, cdl);
		final TcpClient owner = this;
		for (String fs : fileList) {
			final String fullname = pwd + (specifiedSingleFile ? Utils.getBasename(fs) : fs);
			Runnable r = new Runnable() {
				@Override
				public void run() {
					Exception thr = null;
					for (int i = 0; i < retry; i++) {
						try {
							ClientDwfileHandler h = new ClientDwfileHandler(owner, fullname, fs);
							h.run();
							thr = null;
							break;
						} catch (Exception e) {
							if (isDebug()) {
								logger.error("dw {} with {} failed, {}", path, fs, e);
							} else {
								logger.error("dw {} with {} failed, {}", path, fs, e.getMessage());
							}
							thr = e;
						}
					}
					if (thr != null) {
						ud.add(fs);
					}
					cdl.countDown();
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
		File f = new File(path);
		String origDir = "";
		if (f.isFile()) {
			origDir = Utils.getDirname(path);
		} else if (f.isDirectory()) {
			origDir = path.substring(0, path.length() - Utils.getBasename(path).length());
		} else {
			return;
		}

		List<String> l = new ArrayList<String>();
		Utils.getFiles(path, l);
		logger.debug("Locate {} file(s) for {}", l.size(), path);
		if (l.size() == 0) {
			return;
		}

		List<String> fl = l;
		if (cvsExclude) {
			fl = new ArrayList<String>();
			for (String fs : l) {
				boolean cvsIgnore = false;
				for (String cvsfnm : CVS_FOLDERS) {
					if (fs.contains(cvsfnm)) {
						cvsIgnore = true;
						break;
					}
				}
				if (cvsIgnore) {
					continue;
				}
				fl.add(fs);
			}
		}

		List<String> ud = new ArrayList<String>();
		CountDownLatch cdl = new CountDownLatch(fl.size());
		startPerct(fl.size(), cdl);
		for (String fs : fl) {
			final String fn = origDir == null || origDir.length() == 0 ? fs : fs.substring(origDir.length() - 1);
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
								stat = ChangeManager.getClientChanged(fs);
								if (stat == 0) {
									up = false;
								}
							}
							if (up) {
								ClientUpHandler h = new ClientUpHandler(owner, fs, fn);
								h.run();
								if (h.isResult() && stat > 0) {
									ChangeManager.writeClientChangelog(fs, h.getFileChksum());
								}
								thr = null;
							}
							break;
						} catch (Exception e) {
							if (isDebug()) {
								logger.error("up {} with {} failed, {}", path, fs, e);
							} else {
								logger.error("up {} with {} failed, {}", path, fs, e.getMessage());
							}
							thr = e;
						}
					}
					if (thr != null) {
						ud.add(fs);
					}
					cdl.countDown();
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
			logger.debug("{} file(s) up over, {} failed {}", fl.size(), ud.size(), uds);
		}
	}

}
