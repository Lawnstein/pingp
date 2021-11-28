/**
 * Copyright 2005-2021 Client Service International, Inc. All rights reserved. <br>
 * CSII PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.<br>
 * <br>
 * project: pingp <br>
 * create: 2021年11月28日 下午1:41:19 <br>
 * vc: $Id: $
 */

package com.ping.file.serv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ping.file.protocol.Command;
import com.ping.file.protocol.Packet;
import com.ping.file.util.ClientSocket;
import com.ping.file.util.Utils;
import com.ping.sync.ChangeManager;

/**
 * 下载处理器.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
class DwfileHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

	private TcpServer owner;

	private Socket s;
	private String origName = null;
	private String origPath = null;
	private String filename = null;
	private String filePath = null;
	// private boolean fileAppend = false;
	private String fileChksum = null;
	private long fileSize = 0l;
	private long filePos = 0l;
	private long fileChunkIndex = 0l;
	private RandomAccessFile fileRaf = null;

	private Packet recv = null;
	private Packet send = new Packet();
	private int stat = 2;
	// private boolean result = false;
	private boolean handleOver = false;

	public DwfileHandler(Filter filter) {
		this.owner = filter.owner;
		this.s = filter.s;
		this.recv = filter.recv;
	}

	private void close() {
		logger.info("connection {} closed for {}", s, filename);
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

	// public String[] getConf() {
	// File cnf = new File(filePath + ".@{cnf}");
	// if (!cnf.exists()) {
	// return null;
	// }
	//
	// FileInputStream is = null;
	// String content = null;
	// try {
	// is = new FileInputStream(cnf);
	// int sz = is.available();
	// if (sz <= 0)
	// return null;
	//
	// byte[] contentBytes = new byte[sz];
	// int rz = is.read(contentBytes, 0, sz);
	// if (rz < sz)
	// logger.warn("readFileBytes read " + rz + "/" + sz + ", not complete.");
	// content = new String(contentBytes);
	// return content.split("[,]");
	// } catch (IOException e) {
	// } finally {
	// if (is != null) {
	// try {
	// is.close();
	// } catch (IOException e) {
	// }
	// }
	// }
	// return null;
	// }

	// public void delConf() {
	// File cnf = new File(filePath + ".@{cnf}");
	// if (!cnf.exists()) {
	// return;
	// }
	// cnf.delete();
	// }
	//
	// public void writeConf(String content) throws IOException {
	// File cnf = new File(filePath + ".@{cnf}");
	// BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), owner.DEFAULT_FILE_ENCODING));
	// writer.write(content);
	// writer.close();
	// writer = null;
	// }
	//
	// public void writeBytes(byte[] contents) throws IOException {
	// if (fileOut == null) {
	// File file = new File(filePath);
	// fileOut = new FileOutputStream(file.getAbsolutePath(), fileAppend);
	// }
	// if (contents == null || contents.length == 0) {
	// return;
	// }
	// fileOut.write(contents);
	// fileOut.flush();
	// }

	// private void confirmList() {
	// if (Command.DWLIST.equals(recv.command)) {
	// return;
	// }
	//
	// origName = recv.filename;
	// origPath = Utils.getCanonicalPath(ChangeManager.getBaseDir() + recv.filename);
	//
	// send = recv.clone();
	// send.command = Command.DWLIST;
	// send.chunkIndex = 0;
	// StringBuilder sb = new StringBuilder();
	// if (Utils.fileExists(origPath)) {
	// send.chunkIndex = 1;
	// sb.append(origPath.substring(ChangeManager.getBaseDir().length()));
	// } else if (Utils.dirExists(origPath)) {
	// List<String> l = new ArrayList<String>();
	// Utils.getFiles(origPath, l);
	// logger.debug("Locate {} file(s) for {} .", l.size(), filePath);
	// if (l.size() == 0) {
	// send.cmdResult = false;
	// send.cmdMesg = "no file found.";
	// logger.error(send.cmdMesg);
	// return;
	// }
	// for (String s : l) {
	// sb.append(s.substring(ChangeManager.getBaseDir().length()));
	// sb.append('\b');
	// }
	// send.chunkIndex = l.size();
	// } else {
	// send.cmdResult = false;
	// send.cmdMesg = "no file found.";
	// logger.error(send.cmdMesg);
	// return;
	// }
	// try {
	// send.chunkBytes = sb.toString().getBytes(Utils.DEFAULT_FILE_ENCODING);
	// } catch (UnsupportedEncodingException e) {
	// send.cmdResult = false;
	// send.cmdMesg = "down file list on encoding failed, " + e.getMessage();
	// logger.error(send.cmdMesg);
	// return;
	// }
	// logger.info("file {} file count {}", filename, send.chunkIndex);
	// }

	private void confirmChunk() {
		if (!Command.DWCHUNK.equals(recv.command)) {
			return;
		}

		filename = recv.filename;
		filePath = ChangeManager.getBaseDir() + recv.filename;
		fileChksum = recv.chksum;
		fileChunkIndex = recv.chunkIndex;

		send = recv.clone();
		send.filename = null;
		send.chksum = fileChksum;
		send.chunkIndex = fileChunkIndex;
		if (!Utils.fileExists(filePath)) {
			send.cmdResult = false;
			send.cmdMesg = "down file " + filename + " not exists.";
			logger.error(send.cmdMesg);
			return;
		}

		stat = 2;
		if (fileChksum != null && fileChksum.length() > 0) {
			stat = owner.isSync() ? ChangeManager.getServChanged(filename, fileChksum) : fileChksum.equals(Utils.chksum(filePath)) ? 0 : 2;
		} else {
			fileChksum = Utils.chksum(filePath);
			send.chksum = fileChksum;
		}
		if (stat == 2) {
			fileChunkIndex = 0;
			send.chunkIndex = 0;
		} else if (stat == 1 && owner.isSync()) {
			ChangeManager.writeServChangelog(filename, fileChksum);
		}

		try {
			fileRaf = new RandomAccessFile(filePath, "r");
			fileSize = fileRaf.length();
			filePos = fileChunkIndex * Utils.DEFAULT_CHUNK_SIZE;
			if (filePos >= fileSize) {
				send.cmdResult = false;
				send.cmdMesg = "down file " + filename + ", position exeed the file size.";
				logger.error(send.cmdMesg);
				return;
			}
			if (filePos > 0) {
				fileRaf.seek(filePos);
			}
		} catch (IOException e) {
			send.cmdResult = false;
			send.cmdMesg = "down file " + filename + ", RandomAccess failed, " + e.getMessage();
			logger.error(send.cmdMesg);
			return;
		}

		logger.info("file {} chunkIndex expect  {}, real {}", filename, recv.chunkIndex, send.chunkIndex);
	}

	private void handleData() {
		if (!Command.DWDATA.equals(recv.command)) {
			return;
		}

		send = recv.clone();
		send.filename = null;

		int cursize = (int) ((fileSize - filePos) > Utils.DEFAULT_CHUNK_SIZE ? Utils.DEFAULT_CHUNK_SIZE : (fileSize - filePos));
		if (cursize > 0) {
			send.chunkBytes = new byte[cursize];
			try {
				fileRaf.read(send.chunkBytes);
				send.chunkIndex = fileChunkIndex;
			} catch (IOException e) {
				send.cmdResult = false;
				send.cmdMesg = "down file " + filename + ", read failed, " + e.getMessage();
				logger.error(send.cmdMesg);
			}
			fileChunkIndex++;
			filePos += cursize;
		} else {
			send.chunkIndex = Long.MAX_VALUE;
			handleOver = true;
		}
	}

	@Override
	public void run() {
		try {
			for (int i = 0; i < 1; i++) {
				confirmChunk();
				ClientSocket.sendPacket(s, send);
				logger.debug("Sended [{}], {}.", send, s);
				if (!send.cmdResult) {
					break;
				}

				recv = ClientSocket.recvPacket(s, owner.timeout);
				logger.debug("Recv [{}], {}", recv, s);

				while (recv.cmdResult && !handleOver) {
					handleData();
					ClientSocket.sendPacket(s, send);
					logger.debug("Sended [{}], {}.", send, s);

					if (handleOver) {
						break;
					}

					recv = ClientSocket.recvPacket(s, owner.timeout);
					logger.debug("Recv [{}], {}", recv, s);
				}
				break;
			}
		} catch (Throwable th) {
			logger.error("dwfile {} failed, {}", filename, th.getMessage());
		} finally {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			close();
		}
	}

}