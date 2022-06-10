/**
 * Copyright 2005-2021 Client Service International, Inc. All rights reserved. <br>
 * CSII PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.<br>
 * <br>
 * project: pingp <br>
 * create: 2021年11月28日 下午1:27:09 <br>
 * vc: $Id: $
 */

package com.ping.file.client;

import com.ping.file.protocol.Command;
import com.ping.file.protocol.Packet;
import com.ping.file.util.ClientSocket;
import com.ping.file.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.List;

/**
 * 上传处理器.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
class ClientUplistHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(ClientUplistHandler.class);

	private TcpClient owner;

	private Socket s;
	private Long chunkSize;
	private String dirname;
//	private ByteBuffer fileBytes;
	private byte[] fileBytes = null;
	private long fileSize = 0l;
	private long filePos = 0l;
	private Packet send = null;
	private Packet recv = null;
	private boolean handleOver = false;
	private List<String> files;


	public ClientUplistHandler(TcpClient owner, String dirname, List<String> files) throws Exception {
		this.owner = owner;
		this.chunkSize = owner.chunkSize == null ? Utils.DEFAULT_CHUNK_SIZE : owner.chunkSize;
		this.s = ClientSocket.connect(owner.ip, owner.port);
		logger.debug("connected to server {}:{} {} for dir {}", owner.ip, owner.port, s, dirname);
		this.dirname = dirname;
		this.files = files;
	}

	public TcpClient getOwner() {
		return owner;
	}

	public void setOwner(TcpClient owner) {
		this.owner = owner;
	}

	private void close() {
		logger.debug("connection {} closed for {}", s, dirname);
		if (s != null) {
			ClientSocket.close(s);
		}
	}


	private void confirmList() {
		send = new Packet();
		send.command = Command.UPLIST;
		// send.filename = filename;
		send.chunkSize = this.chunkSize;
		send.cmdResult = false;

		if (files != null && files.size() == 0) {
			return;
		}

		StringBuilder sb = new StringBuilder();
		for (String s : files) {
			String fn = s.substring(dirname.length());
			if (Utils.isEmpty(fn)) {
				continue;
			}
			logger.debug("file {} ", fn);
			if (sb.length() > 0) {
				sb.append(",");
			}
			sb.append(Utils.encodeBase64(fn));
		}

		try {
			String fileListStr = sb.toString();
			logger.debug("fileListStr {} ", fileListStr);
			fileBytes = fileListStr.getBytes(Utils.DEFAULT_FILE_ENCODING);
			fileSize = fileBytes.length;
			filePos = 0;
			send.filesize = fileSize;
			send.cmdResult = true;
		} catch (UnsupportedEncodingException e) {
			send.cmdResult = false;
			send.cmdMesg = "upload file list on encoding failed, " + e.getMessage();
			logger.error(send.cmdMesg);
			return;
		}
		logger.debug("file {} list count {}", dirname, files.size());
	}


	public void handleData() {
		int cursize = (int) (fileSize - filePos > chunkSize ? chunkSize : fileSize - filePos);
		if (cursize < 0) {
			cursize = 0;
		}
		if (cursize > 0) {
			send.chunkBytes = new byte[cursize];
			// send.filepos = filePos;
			System.arraycopy(fileBytes, (int) filePos, send.chunkBytes, 0, cursize);

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

				recv = ClientSocket.recvPacket(s, owner.timeout);
				while (recv.cmdResult) {
					if (recv.filepos != null && recv.filepos == Long.MAX_VALUE) {
						break;
					}

					recv = ClientSocket.recvPacket(s, owner.timeout);
					logger.debug("Recv {}", recv);
				}

				break;
			}

			logger.debug("uplist {} over.", dirname);
		} catch (Throwable th) {
			if (owner.isDebug()) {
				logger.error("uplist {} failed, {}", dirname, th);
			} else {
				logger.error("dwlist {} failed, {}", dirname, th.getMessage());
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
