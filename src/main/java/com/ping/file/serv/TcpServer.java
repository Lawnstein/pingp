
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

/**
 * TCP服务.
 *
 * @author Lawnstein.Chan
 * @version $Revision:$
 */
public class TcpServer {
	private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

	protected static String DEFAULT_FILE_ENCODING = "UTF-8";
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

	/**
	 * TCP请求处理并发处理线程池.
	 */
	private ExecutorService handlePool = null;

	private ServerSocket server = null;

	private volatile boolean alived = false;

	class Handler implements Runnable {
		private Socket s;
		private String path = null;
		private boolean fileAppend = false;
		private String fileChksum = null;
		private long fileChunkIndex = 0l;
		private FileOutputStream fileOut = null;
		private boolean fileOver = false;
		private Packet recv = null;
		private Packet send = new Packet();

		public Handler(Socket socket) {
			this.s = socket;
		}

		private void close() {
			logger.debug("connection {} closed for {}", s, path);
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

		public boolean exists(String path) {
			return exists(new File(path));
		}

		public boolean exists(File path) {
			return path.exists();
		}

		public String getBasename(String path) {
			if (path == null || path.length() == 0)
				return null;
			String[] names = path.split("[\\\\/]");
			if (names == null || names.length == 0)
				return null;
			return names[names.length - 1];
		}

		public String getDirname(String path) {
			if (path == null || path.length() == 0)
				return null;
			String basename = getBasename(path);
			if (basename == null)
				return null;
			return path.substring(0, path.length() - basename.length());
		}

		public boolean mkdirsForFile(String absoluteFile) {
			String parent = getDirname(absoluteFile);
			logger.trace("The file " + absoluteFile + " directory " + parent + ", try to mkdirs ...");
			return mkdirs(parent);
		}

		public boolean mkdirs(String absoluteDirectory) {
			return mkdirs(new File(absoluteDirectory));
		}

		public boolean mkdirs(File dir) {
			if (dir == null)
				return false;
			if (dir.exists()) {
				if (!dir.isDirectory()) {
					logger.error("The directory " + dir.getAbsolutePath() + " exist and not a directory, cannot create dir.");
					return false;
				} else {
					return true;
				}
			}
			boolean result = dir.mkdirs();
			logger.debug("The directory " + dir.getAbsolutePath() + " not exist, mkdirs " + result);
			return result;
		}

		public boolean createNewFile(String absoluteFile) {
			if (!mkdirsForFile(absoluteFile)) {
				logger.error("Create directory for file " + absoluteFile + " failed.");
				return false;
			}
			try {
				return new File(absoluteFile).createNewFile();
			} catch (IOException e) {
				logger.error("createNewFile for " + absoluteFile + " IOException:", e);
			}
			return false;
		}

		public String[] getConf() {
			File cnf = new File(path + ".@{cnf}");
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
			File cnf = new File(path + ".@{cnf}");
			if (!cnf.exists()) {
				return;
			}
			cnf.delete();
		}

		public void writeConf(String content) throws IOException {
			File cnf = new File(path + ".@{cnf}");
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), DEFAULT_FILE_ENCODING));
			writer.write(content);
			writer.close();
			writer = null;
		}

		public void writeBytes(byte[] contents) throws IOException {
			if (fileOut == null) {
				File file = new File(path);
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
			if (recv.command.equals(Command.CONFIRMCHUNK)) {
				fileChksum = recv.chksum;
				path = dir + "//" + recv.lfilename;
				send = recv.clone();
				send.lfilename = null;
				if (!mkdirsForFile(path)) {
					if (!mkdirsForFile(path)) {
						send.cmdResult = false;
						send.cmdMesg = "mdkir for " + path + " failed.";
						logger.error(send.cmdMesg);
						return;
					}
				}
				if (exists(path)) {
					String[] ci = getConf();
					if (ci == null) {
						if (fileChksum.equals(Utils.chksum(path))) {
							fileOver = true;
							send.chunkIndex = Long.MAX_VALUE;
							send.cmdMesg = "file " + path + " exist and no changes.";
							logger.debug(send.cmdMesg);
							return;
						}
					} else if (fileChksum.equals(ci[1])) {
						fileAppend = true;
						fileChunkIndex = Long.valueOf(ci[0]) + 1;
						logger.debug("file " + path + " continue to transfer.");
					} else {
						fileAppend = false;
						fileChunkIndex = 0;
					}
				} else {
					fileAppend = false;
					fileChunkIndex = 0;
				}
				send.chunkIndex = fileChunkIndex;
				logger.debug("file " + path + " expect chunkIndex " + send.chunkIndex);
			} else if (recv.command.equals(Command.UPLOADFILE)) {
				if (recv.chunkBytes == null || recv.chunkBytes.length == 0) {
					delConf();
					fileOver = true;
					logger.debug("file " + path + " recv empty, maybe complete.");

					send = recv.clone();
					send.chunkIndex = Long.MAX_VALUE;
					send.cmdMesg = "file " + path + " write over.";
				} else {
					send = recv.clone();
					try {
						writeBytes(recv.chunkBytes);
						logger.debug("file " + path + " write chunkIndex " + fileChunkIndex + ", " + recv.chunkBytes.length + " byte(s)");
						fileChunkIndex++;
						writeConf(fileChunkIndex + "," + fileChksum);
						send.chunkIndex = fileChunkIndex;
					} catch (IOException e) {
						send.cmdResult = false;
						send.cmdMesg = "file " + path + " write failed, " + e.getMessage();
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
				logger.debug("Close {}", s);
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
							logger.debug("Accepted connection " + s);
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
