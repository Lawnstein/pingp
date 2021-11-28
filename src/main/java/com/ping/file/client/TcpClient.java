
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
	protected String path;
	protected int timeout = Utils.DEFAULT_TIMEOUT_SEC;
	protected int retry = 30;
	protected boolean sync = true;

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

//	public boolean exists(String path) {
//		return exists(new File(path));
//	}
//
//	public boolean exists(File path) {
//		return path.exists();
//	}
//
//	public String getBasename(String path) {
//		if (path == null || path.length() == 0)
//			return null;
//		String[] names = path.split("[\\\\/]");
//		if (names == null || names.length == 0)
//			return null;
//		return names[names.length - 1];
//	}
//
//	public String getDirname(String path) {
//		if (path == null || path.length() == 0)
//			return null;
//		String basename = getBasename(path);
//		if (basename == null)
//			return null;
//		return path.substring(0, path.length() - basename.length());
//	}
//
//	public void getFiles(String path, List<String> list) {
//		File file = new File(path);
//		if (file.isDirectory()) {
//			File[] files = file.listFiles();
//			for (int i = 0; i < files.length; i++) {
//				if (files[i].isDirectory()) {
//					getFiles(files[i].getPath(), list);
//				} else {
//					list.add(files[i].getPath());
//				}
//			}
//		} else if (file.isFile()) {
//			list.add(file.getPath());
//		} else if (!file.exists()) {
//			logger.error("{} not exists or cannot read.", path);
//		} else {
//			logger.warn("Unsupprted file type (not common file and directory) for {}", path);
//		}
//	}

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
								Handler h = new Handler(ip, port, s, fn);
								h.run();
								if (h.result && stat > 0) {
									ChangeManager.writeClientChangelog(s, h.fileChksum);
								}
								thr = null;
							}
							break;
						} catch (Exception e) {
							logger.error("handle " + s + " failed, " + e.getMessage());
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
		String uds = "";
		for (String s : ud) {
			uds += "\n" + s;
		}
		logger.debug("{} file(s) uploaded over, {} failed {}", l.size(), ud.size(), uds);
	}

	class Handler implements Runnable {
		private Socket s;
		private String path;
		private File file = null;
		private String fileChksum = null;
		private long fileSize = 0l;
		private long fileChunkIndex = 0l;
		private RandomAccessFile fileRaf = null;
		private Packet send = null;
		private Packet recv = null;
		private boolean result = false;

		public Handler(String ip, int port, String path, String rfilename) throws Exception {
			this.s = ClientSocket.connect(ip, port);
			logger.debug("connected to server {}:{} {} for file {}, rfile {}", ip, port, s, path, rfilename);
			this.path = rfilename;
			this.file = new File(path);
			this.fileChksum = Utils.chksum(path);
			this.fileRaf = new RandomAccessFile(file, "r");
			this.fileSize = fileRaf.length();
			this.fileChunkIndex = 0l;
			logger.debug("local file {}, fileSize {}, fileChksum {}", path, fileSize, fileChksum);
		}

		private void close() {
			if (s != null) {
				ClientSocket.close(s);
			}
			if (fileRaf != null) {
				try {
					fileRaf.close();
				} catch (IOException e) {
				}
			}
		}

		private void recv() throws Throwable {
			byte[] hb = new byte[HEADLENGTH];
			int r = ClientSocket.read(s, hb, 0, 8, timeout);
			int isz = Integer.valueOf(new String(hb, "UTF-8"));
			byte[] db = new byte[isz];
			r = ClientSocket.read(s, db, 0, isz, timeout);
			recv = Packet.valueOf(db);
		}

		private void send() throws Throwable {
			if (send == null) {
				return;
			}
			byte[] db = send.getBytes();
			String hs = "" + db.length;
			if (hs.length() < HEADLENGTH) {
				hs = "00000000".substring(hs.length()) + hs;
			}
			byte[] hb = hs.getBytes("UTF-8");
			ClientSocket.write(s, hb, 0, HEADLENGTH);
			if (db.length > 0) {
				ClientSocket.write(s, db, 0, db.length);
			}
		}

		@Override
		public void run() {
			try {
				send = new Packet();
				send.command = Command.UPCHUNK;
				send.cmdResult = true;
				send.cmdMesg = null;
				send.lfilename = path;
				send.chksum = fileChksum;
				// send.filesize = fileSize;
				send.chunkIndex = 0;
				send.chunkBytes = null;
				send();
				logger.debug("first send {}", send);
				recv();
				logger.debug("first recv {}", recv);
				do {
					if (!recv.cmdResult) {
						throw new RuntimeException("Remote fail : " + recv.cmdMesg);
					}
					if (recv.chunkIndex == Long.MAX_VALUE) {
						break;
					}

					if (fileChunkIndex != recv.chunkIndex) {
						fileChunkIndex = recv.chunkIndex;
						if (fileChunkIndex > 0) {
							logger.debug("reset position to {}/{}", fileChunkIndex * CHUNKSIZE, fileSize);
							fileRaf.seek(fileChunkIndex * CHUNKSIZE);
						}
					}
					send = recv.clone();
					send.command = Command.UPDATA;
					send.chunkIndex = fileChunkIndex;

					long readSize = fileSize - fileChunkIndex * CHUNKSIZE;
					if (readSize > CHUNKSIZE) {
						readSize = CHUNKSIZE;
					}
					if (readSize > 0) {
						send.chunkBytes = new byte[(int) readSize];
						int realRead = fileRaf.read(send.chunkBytes);
						fileChunkIndex++;
						logger.debug("read {} byts(s), current position {}/{}", realRead, fileChunkIndex * CHUNKSIZE, fileSize);
					}
					send();
					if (readSize == 0) {
						break;
					}
					recv();
					logger.debug("general recv {}", recv);
				} while (recv.chunkIndex > 0 && recv.chunkIndex < Long.MAX_VALUE);

				logger.debug("over.");
				result = true;
			} catch (Throwable e) {
				throw new RuntimeException("send or recv Packet failed, " + e.getMessage());
			} finally {
				close();
			}

		}
	}

}
