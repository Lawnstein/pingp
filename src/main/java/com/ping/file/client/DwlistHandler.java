/**
 * Copyright 2005-2021 Client Service International, Inc. All rights reserved. <br>
 * CSII PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.<br>
 * <br>
 * project: pingp <br>
 * create: 2021年11月28日 下午1:27:09 <br>
 * vc: $Id: $
 */

package com.ping.file.client;

import java.net.Socket;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ping.file.protocol.Command;
import com.ping.file.protocol.Packet;
import com.ping.file.util.ClientSocket;

/**
 * 下载处理器.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
class DwlistHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(DwlistHandler.class);

	private TcpClient owner;

	private Socket s;
	private String path;
	private ByteBuffer fileBytes;
	// private long fileChunkIndex = 0l;
	private Packet send = null;
	private Packet recv = null;
	// private boolean result = false;

	public DwlistHandler(TcpClient owner, String path) throws Exception {
		this.owner = owner;
		this.s = ClientSocket.connect(owner.ip, owner.port);
		logger.debug("connected to server {}:{} {} for file {}", owner.ip, owner.port, s, path);
		this.path = path;
		// this.fileChunkIndex = 0l;
		logger.debug("local file {}", path);
	}

	public TcpClient getOwner() {
		return owner;
	}

	public void setOwner(TcpClient owner) {
		this.owner = owner;
	}

	private void close() {
		if (s != null) {
			ClientSocket.close(s);
		}
	}

	@Override
	public void run() {
		try {
			send = new Packet();
			send.command = Command.DWLIST;
			send.filename = path;
			ClientSocket.sendPacket(s, send);
			logger.debug("first send {}", send);
			recv = ClientSocket.recvPacket(s, owner.timeout);
			fileBytes = ByteBuffer.allocate(1024);
			logger.debug("first recv {}", recv);
			while (recv.cmdResult) {
				if (recv.chunkIndex == Long.MAX_VALUE) {
					break;
				}
				if (recv.chunkBytes != null && recv.chunkBytes.length > 0) {
					fileBytes.put(recv.chunkBytes);
				}

				send = recv.clone();
				send.command = Command.DWLIST;
				send.chunkIndex = recv.chunkIndex + 1;
				ClientSocket.sendPacket(s, send);

				recv = ClientSocket.recvPacket(s, owner.timeout);
				logger.debug("general recv {}", recv);
			}

			if (fileBytes != null && fileBytes.position() > 0) {
				String fileListStr = new String(fileBytes.array());
				owner.fileList = fileListStr.split("\b");
			}

			logger.debug("file {} list over.", path);
		} catch (Throwable e) {
			throw new RuntimeException("send or recv Packet failed, " + e.getMessage(), e);
		} finally {
			close();
		}

	}

}
