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
class ServDwfileHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

	private TcpServer owner;

	private Socket s;
	private Long chunkSize;
	// private String origName = null;
	// private String origPath = null;
	private String filename = null;
	private String filePath = null;
	// private boolean fileAppend = false;
	private String fileChksum = null;
	private long filePos = 0l;
	private long fileSize = 0l;
	private RandomAccessFile fileRaf = null;

	private Packet recv = null;
	private Packet send = new Packet();
	private int stat = 2;
	// private boolean result = false;
	private boolean handleOver = false;

	public ServDwfileHandler(ServFilter filter) {
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

	private void confirmChunk() {
		if (!Command.DWCHUNK.equals(recv.command)) {
			return;
		}

		filename = recv.filename;
		filePath = ChangeManager.getBaseDir() + recv.filename;
		fileChksum = recv.chksum;
		fileSize = recv.filesize;
		filePos = recv.filepos;
		if (recv.chunkSize != null) {
			this.chunkSize = recv.chunkSize;
		} else {
			this.chunkSize = Utils.DEFAULT_CHUNK_SIZE;
		}

		send = recv.clone();
		send.filename = null;
		if (!Utils.fileExists(filePath)) {
			send.cmdResult = false;
			send.cmdMesg = "down file " + filename + " not exists.";
			logger.error("{}, filePath {}", send.cmdMesg, filePath);
			return;
		}

		int stat = -1;
		if (owner.isSync()) {
			Object[] chgs = ChangeManager.getServChangedWithDwn(filename, fileChksum);
			stat = (int) chgs[0];
			fileChksum = (String) chgs[1];
		} else {
			String fcs = Utils.chksum(filePath);
			stat = fcs.equals(fileChksum) ? 1 : 3;
			fileChksum = fcs;
		}
		if (stat == 2 || stat == 3) {
			filePos = 0;
		}
		if (owner.isSync() && (stat == 1 || stat == 3)) {
			ChangeManager.writeServChangelog(filename, fileChksum);
		}

		try {
			fileRaf = new RandomAccessFile(filePath, "r");
			if (fileSize == fileRaf.length() && (stat == 0 || stat == 1)) {
				send.cmdResult = false;
				send.cmdMesg = "down file " + filename + " not changed, ignore.";
				logger.debug(send.cmdMesg);
				return;
			}
			
			if (fileSize != fileRaf.length()) {
				filePos = 0l;
				fileSize = fileRaf.length();
			}
			if (filePos >= fileSize) {
				send.cmdResult = false;
				send.cmdMesg = "down file " + filename + ", position exeed the file size, ignore";
				logger.error(send.cmdMesg);
				return;
			}
			if (filePos > 0) {
				fileRaf.seek(filePos);
			}
			send.filepos = filePos;
			send.filesize = fileSize;
			send.chksum = fileChksum;
		} catch (IOException e) {
			send.cmdResult = false;
			send.cmdMesg = "down file " + filename + ", RandomAccess failed, " + e.getMessage();
			logger.error(send.cmdMesg);
			return;
		}

		logger.debug("file {} filePos expect {}, real {}", filename, recv.filepos, send.filepos);
	}

	private void handleData() {
		if (!Command.DWDATA.equals(recv.command)) {
			return;
		}

		send = recv.clone();
		send.filename = null;

		int cursize = (int) ((fileSize - filePos) > chunkSize ? chunkSize : (fileSize - filePos));
		if (cursize > 0) {
			send.chunkBytes = new byte[cursize];
			try {
				fileRaf.read(send.chunkBytes);
				send.filepos = filePos;
			} catch (IOException e) {
				send.cmdResult = false;
				send.cmdMesg = "down file " + filename + ", read failed, " + e.getMessage();
				logger.error(send.cmdMesg);
			}
			filePos += cursize;
		} else {
			send.filepos = Long.MAX_VALUE;
			handleOver = true;
		}
	}

	@Override
	public void run() {
		try {
			for (;;) {
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
			
			logger.debug("dwfile {} over", filename);
		} catch (Throwable th) {
			if (owner.isDebug()) {
				logger.error("dwfile {} failed, {}", filename, th);			
			} else {
				logger.error("dwfile {} failed, {}", filename, th.getMessage());
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