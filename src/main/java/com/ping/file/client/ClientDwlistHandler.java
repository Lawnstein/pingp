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
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ping.file.protocol.Command;
import com.ping.file.protocol.Packet;
import com.ping.file.util.ClientSocket;
import com.ping.file.util.Utils;

/**
 * 下载处理器.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
class ClientDwlistHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(ClientDwlistHandler.class);

	private TcpClient owner;

	private Socket s;
	private Long chunkSize;
	private String path;
	private ByteBuffer fileBytes;
	private Packet send = null;
	private Packet recv = null;

	public ClientDwlistHandler(TcpClient owner, String path) throws Exception {
		this.owner = owner;
		this.chunkSize = owner.chunkSize == null ? Utils.DEFAULT_CHUNK_SIZE : owner.chunkSize;
		this.s = ClientSocket.connect(owner.ip, owner.port);
		logger.debug("connected to server {}:{} {} for file {}", owner.ip, owner.port, s, path);
		this.path = path;
		logger.debug("local file {}", path);
	}

	public TcpClient getOwner() {
		return owner;
	}

	public void setOwner(TcpClient owner) {
		this.owner = owner;
	}

	private void close() {
		logger.debug("connection {} closed for {}", s, path);
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
			send.chunkSize = this.chunkSize;
			ClientSocket.sendPacket(s, send);
			logger.debug("first send {}", send);
			recv = ClientSocket.recvPacket(s, owner.timeout);
//			fileBytes = ByteBuffer.allocate(1024*1024*1024);
			fileBytes = ByteBuffer.allocate((int) (recv.filesize > 0 ? recv.filesize : 1024));
			logger.debug("first recv {}", recv);
			while (recv.cmdResult) {
				if (recv.filepos  != null && recv.filepos == Long.MAX_VALUE) {
					break;
				}
				if (recv.chunkBytes != null && recv.chunkBytes.length > 0) {
					fileBytes.put(recv.chunkBytes);
				}

				send = recv.clone();
				send.command = Command.DWLIST;
//				send.filepos = recv.filepos + ;
				ClientSocket.sendPacket(s, send);
				logger.debug("send {}", send);

				recv = ClientSocket.recvPacket(s, owner.timeout);
				logger.debug("recv {}", recv);
			}

			if (fileBytes != null && fileBytes.position() > 0) {
				String fileListStr = new String(fileBytes.array(), Utils.DEFAULT_FILE_ENCODING);
				logger.debug("fileListStr {}", fileListStr);
				String[] fileListArray = fileListStr.split("[,]");
				List<String> fileL = new ArrayList<String>();
				if (fileListArray != null && fileListArray.length > 0) {
					for (int i = 0; i < fileListArray.length; i++) {
						String fs = fileListArray[i];
						if (fs == null || fs.trim().length() == 0) {
							continue;
						}
						fs = Utils.decodeBase64(fs.trim());
						fileL.add(fs);
						logger.debug("valid file {}/{}  {}", i, fileListArray.length, fs);
					}
				}
				if (fileL.size() > 0) {
					owner.fileList = new String[fileL.size()];
					fileL.toArray(owner.fileList);
				}
			}

			logger.debug("file {} list over.", path);
		} catch (Throwable e) {
			logger.error("file {} list failed.", path, e);
			throw new RuntimeException("file " + path + " list failed, send or recv Packet failed, " + e.getMessage(), e);
		} finally {
			close();
		}

	}

}
