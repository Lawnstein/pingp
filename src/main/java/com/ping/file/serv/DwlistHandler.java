/**
 * Copyright 2005-2021 Client Service International, Inc. All rights reserved. <br>
 * CSII PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.<br>
 * <br>
 * project: pingp <br>
 * create: 2021年11月28日 下午1:41:19 <br>
 * vc: $Id: $
 */

package com.ping.file.serv;

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
class DwlistHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

	private TcpServer owner;

	private Socket s;
	private String filename = null;
	private String filePath = null;
	private byte[] fileBytes = null;
	private long fileSize = 0l;
	private long fileCount = 0l;
	private long filePos = 0l;
	private long fileChunkIndex = 0l;
	private Packet recv = null;
	private Packet send = new Packet();
	private boolean handleOver = false;

	public DwlistHandler(Filter filter) {
		this.owner = filter.owner;
		this.s = filter.s;
		this.recv = filter.recv;
	}

	private void close() {
		logger.info("connection {} closed for {}", s, filename);
		if (s != null) {
			ClientSocket.close(s);
		}
	}

	private void confirmList() {
		if (!Command.DWLIST.equals(recv.command)) {
			return;
		}

		filename = recv.filename;
		filePath = Utils.getCanonicalPath(ChangeManager.getBaseDir() + recv.filename);
		fileChunkIndex = 0;

		send = recv.clone();
		send.chunkIndex = fileChunkIndex;
		StringBuilder sb = new StringBuilder();
		if (Utils.fileExists(filePath)) {
			sb.append(filePath.substring(ChangeManager.getBaseDir().length()));
			fileCount = 1;
		} else if (Utils.dirExists(filePath)) {
			List<String> l = new ArrayList<String>();
			Utils.getFiles(filePath, l);
			fileCount = l.size();
			if (l.size() == 0) {
				send.cmdResult = false;
				send.cmdMesg = "no file found.";
				logger.error(send.cmdMesg);
				return;
			}
			for (String s : l) {
				sb.append(s.substring(ChangeManager.getBaseDir().length()));
				sb.append('\b');
			}
		} else {
			send.cmdResult = false;
			send.cmdMesg = "no file found.";
			logger.error(send.cmdMesg);
			return;
		}
		logger.debug("Locate {} file(s) for {} .", fileCount, filename);

		try {
			fileBytes = sb.toString().getBytes(Utils.DEFAULT_FILE_ENCODING);
			fileSize = fileBytes.length;
			filePos = 0;
		} catch (UnsupportedEncodingException e) {
			send.cmdResult = false;
			send.cmdMesg = "down file list on encoding failed, " + e.getMessage();
			logger.error(send.cmdMesg);
			return;
		}
		logger.info("file {} list count {}", filename, fileCount);
	}

	public void handleData() {
		int cursize = (int) (fileSize - filePos > Utils.DEFAULT_CHUNK_SIZE ? Utils.DEFAULT_CHUNK_SIZE : fileSize - filePos);
		if (cursize < 0) {
			cursize = 0;
		}
		if (cursize > 0) {
			send.chunkBytes = new byte[cursize];
			send.chunkIndex = fileChunkIndex;
			System.arraycopy(fileBytes, (int) filePos, send.chunkBytes, 0, cursize);

			filePos += cursize;
			fileChunkIndex++;
		} else {
			send.chunkIndex = Long.MAX_VALUE;
			handleOver = true;
		}
	}

	@Override
	public void run() {
		try {
			for (int i = 0; i < 1; i++) {
				confirmList();
				if (!send.cmdResult) {
					handleData();
				}
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
			logger.error("dwlist {} failed, {}", filename, th.getMessage());
		} finally {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			close();
		}
	}

}