
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

	class Handler implements Runnable {
		private Socket s;
		private String filename = null;
		private String filePath = null;
		// private boolean fileWrite = true;
		private boolean fileAppend = false;
		private String fileChksum = null;
		private long fileChunkIndex = 0l;
		private FileOutputStream fileOut = null;
		private boolean fileOver = false;
		private Packet recv = null;
		private Packet send = new Packet();
		private int stat = 2;
		private boolean result = false;

		public Handler(Socket socket) {
			this.s = socket;
			logger.info("connection {} accepted", s);
		}

		private void close() {
			logger.info("connection {} closed for {}", s, filename);
			if (fileOut != null) {
				try {
					fileOut.flush();
					fileOut.close();
				} catch (IOException e) {
				}
			}
			if (s != null) {
				ClientSocket.close(s);
			}
		}

		public String[] getConf() {
			File cnf = new File(filePath + ".@{cnf}");
			if (!cnf.exists()) {
				return null;
			}

			FileInputStream is = null;
			String content = null;
			try {
				is = new FileInputStream(cnf);
				int sz = is.available();
				if (sz <= 0)
					return null;

				byte[] contentBytes = new byte[sz];
				int rz = is.read(contentBytes, 0, sz);
				if (rz < sz)
					logger.warn("readFileBytes read " + rz + "/" + sz + ", not complete.");
				content = new String(contentBytes);
				return content.split("[,]");
			} catch (IOException e) {
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
					}
				}
			}
			return null;
		}

		public void delConf() {
			File cnf = new File(filePath + ".@{cnf}");
			if (!cnf.exists()) {
				return;
			}
			cnf.delete();
		}

		public void writeConf(String content) throws IOException {
			File cnf = new File(filePath + ".@{cnf}");
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), DEFAULT_FILE_ENCODING));
			writer.write(content);
			writer.close();
			writer = null;
		}

		public void writeBytes(byte[] contents) throws IOException {
			if (fileOut == null) {
				File file = new File(filePath);
				fileOut = new FileOutputStream(file.getAbsolutePath(), fileAppend);
			}
			if (contents == null || contents.length == 0) {
				return;
			}
			fileOut.write(contents);
			fileOut.flush();
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

		private void call() {
			if (recv.command.equals(Command.UPCHUNK)) {
				filename = recv.lfilename;
				filePath = ChangeManager.getBaseDir() + recv.lfilename;
				fileChksum = recv.chksum;
				
				send = recv.clone();
				send.lfilename = null;
				if (!Utils.mkdirsForFile(filePath)) {
					send.cmdResult = false;
					send.cmdMesg = "mdkir for " + filePath + " failed.";
					logger.error(send.cmdMesg);
					return;
				}
				if (Utils.exists(filePath)) {
					String[] ci = getConf();
					if (ci == null) {
						// if (fileChksum.equals(Utils.chksum(path))) {
						stat = isSync() ? ChangeManager.getServChanged(filename, fileChksum) : fileChksum.equals(Utils.chksum(filePath)) ? 0 : 2;
						if (stat == 0 || stat == 1) {
							fileOver = true;
							// fileWrite = false;
							send.chunkIndex = Long.MAX_VALUE;
							send.cmdMesg = "file " + filename + " exist and no changes.";
							logger.info(send.cmdMesg);
							if (isSync() && stat == 1) {
								ChangeManager.writeServChangelog(filename, fileChksum);
							}
							return;
						}
					} else if (fileChksum.equals(ci[1])) {
						fileAppend = true;
						fileChunkIndex = Long.valueOf(ci[0]) + 1;
						logger.debug("file {} continue to transfer.", filename);
					} else {
						fileAppend = false;
						fileChunkIndex = 0;
					}
				} else {
					fileAppend = false;
					fileChunkIndex = 0;
				}
				send.chunkIndex = fileChunkIndex;
				logger.info("file {} expect chunkIndex {}", filename, send.chunkIndex);
			} else if (recv.command.equals(Command.UPDATA)) {
				if (recv.chunkBytes == null || recv.chunkBytes.length == 0) {
					delConf();
					fileOver = true;
					logger.debug("file {} recv empty, maybe complete.", filename);

					send = recv.clone();
					send.chunkIndex = Long.MAX_VALUE;
					send.cmdMesg = "file " + filename + " write over.";
					if (isSync()) {
						ChangeManager.writeServChangelog(filename, fileChksum);
					}
				} else {
					send = recv.clone();
					try {
						writeBytes(recv.chunkBytes);
						logger.debug("file {} write chunkIndex {}, {} byte(s)", filename, fileChunkIndex, recv.chunkBytes.length);
						fileChunkIndex++;
						writeConf(fileChunkIndex + "," + fileChksum);
						send.chunkIndex = fileChunkIndex;
					} catch (IOException e) {
						send.cmdResult = false;
						send.cmdMesg = "file " + filename + " write failed, " + e.getMessage();
						logger.error(send.cmdMesg);
						return;
					}
				}
			}
		}

		@Override
		public void run() {
			try {
				while (!fileOver && send.cmdResult) {
					recv();
					logger.debug("Recv [{}], {}", recv, s);
					call();
					send();
					logger.debug("Sended [{}], {}.", send, s);
				}
			} catch (Throwable th) {
				logger.error("run failed. {}", th);
			} finally {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
				close();
			}
		}

	}

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
							handlePool.execute(new Handler(s));
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
