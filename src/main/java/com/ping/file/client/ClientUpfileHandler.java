/**
 * Copyright 2005-2021 Client Service International, Inc. All rights reserved. <br>
 * CSII PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.<br>
 * <br>
 * project: pingp <br>
 * create: 2021年11月28日 下午1:27:09 <br>
 * vc: $Id: $
 */

package com.ping.file.client;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ping.file.protocol.Command;
import com.ping.file.protocol.Packet;
import com.ping.file.util.ClientSocket;
import com.ping.file.util.Utils;

/**
 * 上传处理器.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
class ClientUpfileHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(ClientUpfileHandler.class);

	private TcpClient owner;

	private Socket s;
	private Long chunkSize;
	private String path;
	private File file = null;
	private String fileChksum = null;
	private long fileSize = 0l;
	private long filePos = 0l;
	private RandomAccessFile fileRaf = null;
	private Packet send = null;
	private Packet recv = null;
	private boolean result = false;

	public ClientUpfileHandler(TcpClient owner, String path, String rfilename) throws Exception {
		this.owner = owner;
		this.chunkSize = owner.chunkSize == null ? Utils.DEFAULT_CHUNK_SIZE : owner.chunkSize;
		this.s = ClientSocket.connect(owner.ip, owner.port);
		logger.debug("connected to server {}:{} {} for file {}, rfile {}", owner.ip, owner.port, s, path, rfilename);
		this.path = rfilename;
		this.file = new File(path);
		this.fileChksum = Utils.chksum(path);
		this.fileRaf = new RandomAccessFile(file, "r");
		this.fileSize = fileRaf.length();
		this.filePos = 0l;
		logger.debug("local file {}, fileSize {}, fileChksum {}", path, fileSize, fileChksum);
	}

	public String getFileChksum() {
		return fileChksum;
	}

	public boolean isResult() {
		return result;
	}

	private void close() {
		logger.debug("connection {} closed for {}", s, path);
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

	@Override
	public void run() {
		try {
			send = new Packet();
			send.command = Command.UPCHUNK;
			send.cmdResult = true;
			send.cmdMesg = null;
			send.filename = path;
			send.chksum = fileChksum;
			// send.filepos = 0L;
			send.filesize = fileSize;
			send.chunkBytes = null;
			send.chunkSize = this.chunkSize;
			ClientSocket.sendPacket(s, send);
			logger.debug("first send {}", send);
			recv = ClientSocket.recvPacket(s, owner.timeout);
			logger.debug("first recv {}", recv);
			for (;;) {
				if (!recv.cmdResult) {
					throw new RuntimeException("Remote fail : " + recv.cmdMesg);
				}
				if (recv.filepos != null && recv.filepos == Long.MAX_VALUE) {
					break;
				}

				if (recv.filepos != null && filePos != recv.filepos) {
					filePos = recv.filepos;
					if (filePos > 0) {
						logger.debug("reset position to {}/{}", filePos, fileSize);
						fileRaf.seek(filePos);
					}
				}
				send = recv.clone();
				send.command = Command.UPDATA;
				send.filepos = null;

				long readSize = fileSize - filePos;
				if (readSize > chunkSize) {
					readSize = chunkSize;
				}
				if (readSize > 0) {
					send.chunkBytes = new byte[(int) readSize];
					int realRead = fileRaf.read(send.chunkBytes);
					if (realRead != readSize) {
						throw new RuntimeException("expected read " + readSize + " but " + realRead);
					}
					filePos += realRead;
					logger.debug("read {} byts(s), current position {}/{}", realRead, filePos, fileSize);
				}
				ClientSocket.sendPacket(s, send);
				logger.debug("send {}", send);
				if (readSize == 0) {
					break;
				}
				
				recv = ClientSocket.recvPacket(s, owner.timeout);
				logger.debug("recv {}", recv);
			}

			logger.debug("file {} upload over.", path);
			result = true;
		} catch (Throwable e) {
			throw new RuntimeException("send or recv Packet failed, " + e.getMessage(), e);
		} finally {
			close();
		}

	}
}
