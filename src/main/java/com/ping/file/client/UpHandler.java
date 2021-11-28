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
class UpHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(UpHandler.class);

	private TcpClient owner;

	private Socket s;
	private String path;
	private File file = null;
	private String fileChksum = null;
	private long fileSize = 0l;
	private long fileChunkIndex = 0l;
	private RandomAccessFile fileRaf = null;
	private Packet send = null;
	private Packet recv = null;
	private boolean result = false;

	public UpHandler(TcpClient owner, String path, String rfilename) throws Exception {
		this.owner = owner;
		this.s = ClientSocket.connect(owner.ip, owner.port);
		logger.debug("connected to server {}:{} {} for file {}, rfile {}", owner.ip, owner.port, s, path, rfilename);
		this.path = rfilename;
		this.file = new File(path);
		this.fileChksum = Utils.chksum(path);
		this.fileRaf = new RandomAccessFile(file, "r");
		this.fileSize = fileRaf.length();
		this.fileChunkIndex = 0l;
		logger.debug("local file {}, fileSize {}, fileChksum {}", path, fileSize, fileChksum);
	}

//	public TcpClient getOwner() {
//		return owner;
//	}
//
//	public void setOwner(TcpClient owner) {
//		this.owner = owner;
//	}
//
//	public String getPath() {
//		return path;
//	}
//
//	public void setPath(String path) {
//		this.path = path;
//	}
//
	public String getFileChksum() {
		return fileChksum;
	}
//
//	public void setFileChksum(String fileChksum) {
//		this.fileChksum = fileChksum;
//	}
//
//	public long getFileSize() {
//		return fileSize;
//	}
//
//	public void setFileSize(long fileSize) {
//		this.fileSize = fileSize;
//	}
//
//	public long getFileChunkIndex() {
//		return fileChunkIndex;
//	}
//
//	public void setFileChunkIndex(long fileChunkIndex) {
//		this.fileChunkIndex = fileChunkIndex;
//	}

	public boolean isResult() {
		return result;
	}

//	public void setResult(boolean result) {
//		this.result = result;
//	}

	private void close() {
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
			send.chunkIndex = 0;
			send.chunkBytes = null;
			ClientSocket.sendPacket(s, send);
			logger.debug("first send {}", send);
			recv = ClientSocket.recvPacket(s, owner.timeout);
			logger.debug("first recv {}", recv);
			do {
				if (!recv.cmdResult) {
					throw new RuntimeException("Remote fail : " + recv.cmdMesg);
				}
				if (recv.chunkIndex == Long.MAX_VALUE) {
					break;
				}

				if (fileChunkIndex != recv.chunkIndex) {
					fileChunkIndex = recv.chunkIndex;
					if (fileChunkIndex > 0) {
						logger.debug("reset position to {}/{}", fileChunkIndex * owner.CHUNKSIZE, fileSize);
						fileRaf.seek(fileChunkIndex * owner.CHUNKSIZE);
					}
				}
				send = recv.clone();
				send.command = Command.UPDATA;
				send.chunkIndex = fileChunkIndex;

				long readSize = fileSize - fileChunkIndex * owner.CHUNKSIZE;
				if (readSize > owner.CHUNKSIZE) {
					readSize = owner.CHUNKSIZE;
				}
				if (readSize > 0) {
					send.chunkBytes = new byte[(int) readSize];
					int realRead = fileRaf.read(send.chunkBytes);
					fileChunkIndex++;
					logger.debug("read {} byts(s), current position {}/{}", realRead, fileChunkIndex * owner.CHUNKSIZE, fileSize);
				}
				ClientSocket.sendPacket(s, send);
				if (readSize == 0) {
					break;
				}
				recv = ClientSocket.recvPacket(s, owner.timeout);
				logger.debug("general recv {}", recv);
			} while (recv.chunkIndex > 0 && recv.chunkIndex < Long.MAX_VALUE);

			logger.debug("file {} upload over.", path);
			result = true;
		} catch (Throwable e) {
			throw new RuntimeException("send or recv Packet failed, " + e.getMessage(), e);
		} finally {
			close();
		}

	}
}
