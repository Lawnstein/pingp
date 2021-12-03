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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ping.file.protocol.Command;
import com.ping.file.protocol.Packet;
import com.ping.file.util.ClientSocket;
import com.ping.file.util.Utils;
import com.ping.sync.ChangeManager;

/**
 * 上传处理器.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
class ServUpfileHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

	private TcpServer owner;

	private Socket s;
	private Long chunkSize;
	private String filename = null;
	private String filePath = null;
	private boolean fileAppend = false;
	private String fileChksum = null;
	private long fileSize = 0l;
	private long filePos = 0l;
	private FileOutputStream fileOut = null;
	private Packet recv = null;
	private Packet send = new Packet();
	private int stat = 2;
	private boolean handleOver = false;
	// private boolean result = false;

	public ServUpfileHandler(ServFilter filter) {
		this.owner = filter.owner;
		this.s = filter.s;
		this.recv = filter.recv;
	}

	private void close() {
		logger.info("connection {} closed for {}", s, filename);
		if (s != null) {
			ClientSocket.close(s);
		}
		if (fileOut != null) {
			try {
				fileOut.flush();
				fileOut.close();
			} catch (IOException e) {
			}
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

	public void writeConf(String content) throws IOException {
		File cnf = new File(filePath + ".@{cnf}");
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), owner.DEFAULT_FILE_ENCODING));
		writer.write(content);
		writer.close();
		writer = null;
	}

	public void delConf() {
		File cnf = new File(filePath + ".@{cnf}");
		if (!cnf.exists()) {
			return;
		}
		cnf.delete();
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

	private void confirmChunk() {
		if (!Command.UPCHUNK.equals(recv.command)) {
			return;
		}

		filename = recv.filename;
		filePath = ChangeManager.getBaseDir() + recv.filename;
		fileSize = recv.filesize;
		fileChksum = recv.chksum;
		if (recv.chunkSize != null) {
			this.chunkSize = recv.chunkSize;
		} else {
			this.chunkSize = Utils.DEFAULT_CHUNK_SIZE;
		}

		send = recv.clone();
		send.filename = null;
		if (!Utils.mkdirsForFile(filePath)) {
			send.cmdResult = false;
			send.cmdMesg = "mdkir for " + filePath + " failed.";
			logger.error(send.cmdMesg);
			return;
		}
		if (Utils.fileExists(filePath)) {
			String[] ci = getConf();
			if (ci == null) {
				// if (fileChksum.equals(Utils.chksum(path))) {
				stat = owner.isSync() ? ChangeManager.getServChangedWithUp(filename, fileChksum) : fileChksum.equals(Utils.chksum(filePath)) ? 0 : 2;
				if (stat == 0 || stat == 1) {
					handleOver = true;
					// fileWrite = false;
					send.filepos = Long.MAX_VALUE;
					send.cmdMesg = "file " + filename + " exist and no changes.";
					logger.info(send.cmdMesg);
					if (owner.isSync() && stat == 1) {
						ChangeManager.writeServChangelog(filename, fileChksum);
					}
					return;
				}
			} else if (fileChksum.equals(ci[2]) && fileSize == Long.valueOf(ci[1])) {
				fileAppend = true;
				filePos = Long.valueOf(ci[0]);
				logger.debug("file {} continue to transfer.", filename);
			} else {
				fileAppend = false;
				filePos = 0;
			}
		} else {
			fileAppend = false;
			filePos = 0;
		}
		send.filepos = filePos;
		logger.info("file {} expect filePos {}", filename, send.filepos);
	}

	private void handleData() {
		if (!Command.UPDATA.equals(recv.command)) {
			return;
		}
		if (recv.chunkBytes == null || recv.chunkBytes.length == 0) {
			handleOver = true;
			if (fileSize == filePos) {
				delConf();
				logger.debug("file {} recv complete.", filename);
			} else {
				logger.debug("file {} recv over, but not complete.", filename);
			}

			send = recv.clone();
			send.filepos = Long.MAX_VALUE;
			send.cmdMesg = "file " + filename + " write over.";
			if (owner.isSync()) {
				ChangeManager.writeServChangelog(filename, fileChksum);
			}
		} else {
			send = recv.clone();
			try {
				writeBytes(recv.chunkBytes);
				logger.debug("file {} write filePos {}, {} byte(s)", filename, filePos, recv.chunkBytes.length);
				filePos += recv.chunkBytes.length;
				writeConf(filePos + "," + fileSize + "," + fileChksum);
				send.filepos = filePos;
			} catch (IOException e) {
				send.cmdResult = false;
				send.cmdMesg = "file " + filename + " write failed, " + e.getMessage();
				logger.error(send.cmdMesg);
				return;
			}
		}
	}

	@Override
	public void run() {
		try {
			confirmChunk();
			ClientSocket.sendPacket(s, send);
			logger.debug("Sended [{}], {}.", send, s);

			while (send.cmdResult && !handleOver) {
				recv = ClientSocket.recvPacket(s, owner.timeout);
				logger.debug("Recv [{}], {}", recv, s);

				handleData();
				if (!handleOver) {
					ClientSocket.sendPacket(s, send);
					logger.debug("Sended [{}], {}.", send, s);
				}
			}

			logger.debug("upfile {} over.", filename);
		} catch (Throwable th) {
			if (owner.isDebug()) {
				logger.error("upfile {} failed, {}", filename, th);
			} else {
				logger.error("upfile {} failed, {}", filename, th.getMessage());
			}
		} finally {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			close();
		}
	}

}