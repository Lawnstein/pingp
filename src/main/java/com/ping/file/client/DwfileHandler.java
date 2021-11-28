/**
 * Copyright 2005-2021 Client Service International, Inc. All rights reserved. <br>
 * CSII PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.<br>
 * <br>
 * project: pingp <br>
 * create: 2021年11月28日 下午1:27:09 <br>
 * vc: $Id: $
 */

package com.ping.file.client;

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
 * 下载处理器.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
class DwfileHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(DwfileHandler.class);

	private TcpClient owner;

	private Socket s;
	private String filePath;
	private String filename;
	// private File file = null;
	private String fileChksum = null;
	// private long fileSize = 0l;
	private long fileChunkIndex = 0l;
	private boolean fileAppend = false;
	private FileOutputStream fileOut = null;
	private Packet send = null;
	private Packet recv = null;
	private boolean handleOver = false;
	private boolean result = false;

	public DwfileHandler(TcpClient owner, String fullname, String rfilename) throws Exception {
		this.owner = owner;
		this.s = ClientSocket.connect(owner.ip, owner.port);
		logger.debug("connected to server {}:{} {} for  {}", owner.ip, owner.port, s, rfilename);
		this.filePath = fullname;
		this.filename = rfilename;
		this.fileChunkIndex = 0l;
		logger.debug("local file {}", filename);
	}

	public boolean isResult() {
		return result;
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

	public void delConf() {
		File cnf = new File(filePath + ".@{cnf}");
		if (!cnf.exists()) {
			return;
		}
		cnf.delete();
	}

	public void writeConf(String content) throws IOException {
		File cnf = new File(filePath + ".@{cnf}");
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), Utils.DEFAULT_FILE_ENCODING));
		writer.write(content);
		writer.close();
		writer = null;
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

	private void close() {
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

	private void confirmChunk() throws Throwable {
		String[] cf = getConf();
		if (cf != null && cf.length == 2) {
			fileChunkIndex = Long.valueOf(cf[0]);
			fileChksum = cf[1];
		}

		send = new Packet();
		send.command = Command.DWCHUNK;
		send.filename = filename;
		send.chksum = fileChksum;
		send.chunkIndex = fileChunkIndex;
		ClientSocket.sendPacket(s, send);
		logger.debug("first send {}", send);
		recv = ClientSocket.recvPacket(s, owner.timeout);
		logger.debug("first recv {}", recv);
		if (!recv.cmdResult) {
			return;
		}

		if (!recv.chksum.equals(fileChksum)) {
			fileChunkIndex = 0;
			fileChksum = recv.chksum;
		} else {
			fileAppend = true;
		}

		logger.debug("file {} chunkIndex  {}, fileChksum {}", filename, fileChunkIndex, fileChksum);
	}

	private void handleData() throws Throwable {
		if (recv.chunkBytes == null || recv.chunkBytes.length == 0) {
			delConf();
			handleOver = true;
			logger.debug("file {} recv empty, maybe complete.", filename);

			if (owner.isSync()) {
				ChangeManager.writeClientChangelog(filePath, fileChksum);
			}
		} else {
			send = recv.clone();
			send.command = Command.DWDATA;
			try {
				writeBytes(recv.chunkBytes);
				logger.debug("file {} write chunkIndex {}, {} byte(s)", filename, fileChunkIndex, recv.chunkBytes.length);
				fileChunkIndex++;
				writeConf(fileChunkIndex + "," + fileChksum);
				send.chunkIndex = fileChunkIndex;
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

			send = recv.clone();
			send.command = Command.DWDATA;
			send.chunkIndex = fileChunkIndex++;
			ClientSocket.sendPacket(s, send);
			logger.debug("send {}", send);
			recv = ClientSocket.recvPacket(s, owner.timeout);
			logger.debug("recv {}", recv);

			while (recv.cmdResult && !handleOver) {
				handleData();
				if (handleOver) {
					break;
				}

				ClientSocket.sendPacket(s, send);
				logger.debug("send {}", send);
				recv = ClientSocket.recvPacket(s, owner.timeout);
				logger.debug("recv {}", recv);
			}

			logger.debug("file {} down over.", filename);
			result = true;
		} catch (Throwable e) {
			throw new RuntimeException("send or recv Packet failed, " + e.getMessage(), e);
		} finally {
			close();
		}

	}
}
