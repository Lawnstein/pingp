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
class UpfileHandler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

	private TcpServer owner;

	private Socket s;
	private String filename = null;
	private String filePath = null;
	private boolean fileAppend = false;
	private String fileChksum = null;
	private long fileChunkIndex = 0l;
	private FileOutputStream fileOut = null;
	private Packet recv = null;
	private Packet send = new Packet();
	private int stat = 2;
	private boolean handleOver = false;
//	private boolean result = false;

	public UpfileHandler(Filter filter) {
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

	public void delConf() {
		File cnf = new File(filePath + ".@{cnf}");
		if (!cnf.exists()) {
			return;
		}
		cnf.delete();
	}

	public void writeConf(String content) throws IOException {
		File cnf = new File(filePath + ".@{cnf}");
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), owner.DEFAULT_FILE_ENCODING));
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

	private void confirmChunk() {
		if (!Command.UPCHUNK.equals(recv.command)) {
			return;
		}

		filename = recv.filename;
		filePath = ChangeManager.getBaseDir() + recv.filename;
		fileChksum = recv.chksum;

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
				stat = owner.isSync() ? ChangeManager.getServChanged(filename, fileChksum) : fileChksum.equals(Utils.chksum(filePath)) ? 0 : 2;
				if (stat == 0 || stat == 1) {
					handleOver = true;
					// fileWrite = false;
					send.chunkIndex = Long.MAX_VALUE;
					send.cmdMesg = "file " + filename + " exist and no changes.";
					logger.info(send.cmdMesg);
					if (owner.isSync() && stat == 1) {
						ChangeManager.writeServChangelog(filename, fileChksum);
					}
					return;
				}
			} else if (fileChksum.equals(ci[1])) {
				fileAppend = true;
				fileChunkIndex = Long.valueOf(ci[0]) + 1;
				logger.debug("file {} continue to transfer.", filename);
			} else {
				fileAppend = false;
				fileChunkIndex = 0;
			}
		} else {
			fileAppend = false;
			fileChunkIndex = 0;
		}
		send.chunkIndex = fileChunkIndex;
		logger.info("file {} expect chunkIndex {}", filename, send.chunkIndex);
	}

	private void handleData() {
		if (!Command.UPDATA.equals(recv.command)) {
			return;
		}
		if (recv.chunkBytes == null || recv.chunkBytes.length == 0) {
			delConf();
			handleOver = true;
			logger.debug("file {} recv empty, maybe complete.", filename);

			send = recv.clone();
			send.chunkIndex = Long.MAX_VALUE;
			send.cmdMesg = "file " + filename + " write over.";
			if (owner.isSync()) {
				ChangeManager.writeServChangelog(filename, fileChksum);
			}
		} else {
			send = recv.clone();
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
			ClientSocket.sendPacket(s, send);
			logger.debug("Sended [{}], {}.", send, s);

			while (send.cmdResult && !handleOver) {
				recv = ClientSocket.recvPacket(s, owner.timeout);
				logger.debug("Recv [{}], {}", recv, s);
				
				handleData();
				ClientSocket.sendPacket(s, send);
				logger.debug("Sended [{}], {}.", send, s);
			}
		} catch (Throwable th) {
			logger.error("upfile {} failed, {}", filename, th.getMessage());
		} finally {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			close();
		}
	}

}