
package com.ping.file.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ping.file.protocol.Packet;

/**
 * Socket操作.
 * 
 * @author Lawnstein.Chan
 * @version $Revision:$
 */
public class ClientSocket {
	private static final Logger logger = LoggerFactory.getLogger(ClientSocket.class);
	public static final int GRANULARITY = 5;

	public static Socket connect(String address, int port) throws Exception {
		return connect(address, port, true, 0, 30000);
	}

	public static Socket connect(String address, int port, boolean soLingerOn, int soLingerNum, int soTimeout) throws Exception {
		Socket socket = new Socket(address, port);
		socket.setSoLinger(soLingerOn, soLingerNum);
		socket.setSoTimeout(soTimeout);
		return socket;
	}

	public static void close(Socket socket) {
		if (socket != null && !socket.isClosed()) {
			try {
				socket.shutdownOutput();
				socket.shutdownInput();
				socket.close();
			} catch (IOException arg1) {
				logger.error("close socket " + socket + " IOException " + arg1);
			}
		}

	}

	public static byte[] readBytes(Socket connect, int timeout) throws Exception {
		Object bytes = null;

		try {
			boolean e = false;
			int leftime = (timeout < 0 ? 10 : timeout) * 1000;

			for (InputStream ins = connect.getInputStream(); leftime > 0; leftime -= 5) {
				int e1;
				if ((e1 = ins.available()) > 0) {
					byte[] bytes1 = new byte[e1];
					ins.read(bytes1, 0, e1);
					return bytes1;
				}

				Thread.sleep(5L);
			}

			if (leftime <= 0) {
				throw new RuntimeException("read timeout " + timeout + " second(s).");
			} else {
				return (byte[]) bytes;
			}
		} catch (IOException arg5) {
			throw arg5;
		} catch (InterruptedException arg6) {
			throw arg6;
		}
	}

	public static int read(Socket connect, byte[] bytes, int pos, int timeout) throws Exception {
		try {
			boolean e = false;
			boolean rsize = false;
			int leftime = (timeout < 0 ? 10 : timeout) * 1000;

			for (InputStream ins = connect.getInputStream(); leftime > 0; leftime -= 5) {
				int e1;
				if ((e1 = ins.available()) > 0) {
					int rsize1;
					if (e1 > bytes.length - pos) {
						rsize1 = bytes.length - pos;
					} else {
						rsize1 = e1;
					}

					int r = ins.read(bytes, pos, rsize1);
					return r;
				}

				Thread.sleep(5L);
			}

			if (leftime <= 0) {
				throw new RuntimeException("read timeout " + timeout + " second(s).");
			} else {
				return -1;
			}
		} catch (IOException arg8) {
			throw arg8;
		} catch (InterruptedException arg9) {
			throw arg9;
		}
	}

	public static int read(Socket connect, byte[] bytes, int pos, int size, int timeout) throws Exception {
		if (size == 0) {
			return 0;
		} else if (bytes.length < size) {
			throw new RuntimeException("not enough byte array size " + bytes.length + " for expected size " + size);
		} else {
			try {
				int e = 0;
				boolean available = false;
				boolean rsize = false;
				int leftsize = size;
				int leftime = (timeout <= 0 ? 10 : timeout) * 1000;

				for (InputStream ins = connect.getInputStream(); leftime > 0; leftime -= 5) {
					int available1;
					if ((available1 = ins.available()) > 0) {
						int rsize1 = leftsize;
						if (leftsize > available1) {
							rsize1 = available1;
						}

						int r = ins.read(bytes, pos + e, rsize1);
						if (r < 0) {
							throw new RuntimeException("read bytes exception: available " + available1 + ", readed " + r);
						}

						e += r;
						leftsize -= r;
						// logger.trace("read {} byte(s), total read {} byte(s), left {} byte(s).", new Object[] { Integer.valueOf(r), Integer.valueOf(e),
						// Integer
						// .valueOf(leftsize) });
						if (leftsize <= 0) {
							return e;
						}
					}

					Thread.sleep(5L);
				}

				if (leftime <= 0) {
					throw new RuntimeException("read " + size + " bytes timeout " + timeout + " seconds.");
				} else {
					return e;
				}
			} catch (IOException arg11) {
				throw arg11;
			} catch (InterruptedException arg12) {
				throw arg12;
			}
		}
	}

	public static boolean write(Socket connect, byte[] bytes, int pos, int size) throws Exception {
		try {
			OutputStream e = connect.getOutputStream();
			e.write(bytes, pos, size);
			e.flush();
			return true;
		} catch (IOException arg4) {
			throw arg4;
		}
	}

	public static Packet recvPacket(Socket s, int timeout) throws Throwable {
		byte[] hb = new byte[Utils.HEADLENGTH];
		int r = read(s, hb, 0, 8, timeout);
		int isz = Integer.valueOf(new String(hb, "UTF-8"));
		byte[] db = new byte[isz];
		r = read(s, db, 0, isz, timeout);
		return Packet.valueOf(db);
	}

	public static void sendPacket(Socket s, Packet send) throws Throwable {
		if (send == null) {
			return;
		}
		byte[] db = send.getBytes();
		String hs = "" + db.length;
		if (hs.length() < Utils.HEADLENGTH) {
			hs = "00000000".substring(hs.length()) + hs;
		}
		byte[] hb = hs.getBytes("UTF-8");
		ClientSocket.write(s, hb, 0, Utils.HEADLENGTH);
		if (db.length > 0) {
			ClientSocket.write(s, db, 0, db.length);
		}
	}
}
