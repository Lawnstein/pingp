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
import com.ping.sync.ChangeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

/**
 * 下载处理器.
 *
 * @author lawnstein.chan
 * @version $Revision:$
 */
class ClientDwfileHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientDwfileHandler.class);

    private TcpClient owner;
    private boolean sync = false;

    private Socket s;
    private Long chunkSize;
    private String filePath;
    private String filename;
    private long filePos = 0l;
    private long fileSize = 0l;
    private String fileChksum = null;
    private boolean fileAppend = false;
    private FileOutputStream fileOut = null;
    private Packet send = null;
    private Packet recv = null;
    private boolean handleOver = false;
    private boolean result = false;

    public ClientDwfileHandler(TcpClient owner, Boolean sync, String fullname, String rfilename) throws Exception {
        this.owner = owner;
		this.sync = sync == null ? sync : owner.isSync();
        this.chunkSize = owner.chunkSize == null ? Utils.DEFAULT_CHUNK_SIZE : owner.chunkSize;
        this.s = ClientSocket.connect(owner.ip, owner.port);
        logger.debug("connected to server {}:{} {} for  {}", owner.ip, owner.port, s, rfilename);
        this.filePath = fullname;
        this.filename = rfilename;
        this.filePos = 0l;
        this.fileSize = 0l;
        logger.debug("local file {}", fullname);
    }

    public boolean isSync() {
        return this.sync;
    }

    public boolean isResult() {
        return this.result;
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
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cnf.getAbsolutePath(), false), Utils.DEFAULT_FILE_ENCODING));
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

    private void close() {
        logger.debug("connection {} closed for {}", s, filename);
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
        if (cf != null && cf.length == 3) {
            filePos = Long.valueOf(cf[0]);
            fileSize = Long.valueOf(cf[1]);
            fileChksum = cf[2];
            if (filePos > fileSize) {
                filePos = 0l;
                fileSize = 0l;
                fileChksum = null;
            }
        } else if (Utils.fileExists(filePath)) {
            filePos = 0;
            fileSize = Utils.filesize(filePath);
            fileChksum = Utils.chksum(filePath);
        }

        send = new Packet();
        send.command = Command.DWCHUNK;
        send.filename = filename;
        send.filepos = filePos;
        send.filesize = fileSize;
        send.chksum = fileChksum;
        send.chunkSize = this.chunkSize;
        ClientSocket.sendPacket(s, send);
        logger.debug("first send {}", send);
        recv = ClientSocket.recvPacket(s, owner.timeout);
        logger.debug("first recv {}", recv);
        if (!recv.cmdResult) {
            return;
        }

        filePos = recv.filepos;
        fileSize = recv.filesize;
        fileChksum = recv.chksum;
        if (filePos > 0) {
            fileAppend = true;
        }

        Utils.mkdirsForFile(filePath);
        logger.debug("file {} filePos  {}, fileChksum {}", filename, filePos, fileChksum);
    }

    private void handleData() throws Throwable {
        if (recv.chunkBytes == null || recv.chunkBytes.length == 0) {
            handleOver = true;
            if (filePos == fileSize) {
                delConf();
                logger.debug("file {} recv complete.", filename);
            } else {
                logger.debug("file {} recv over, but not complete.", filename);
            }

            if (isSync()) {
                ChangeManager.writeClientChangelog(filePath, fileChksum);
            }
        } else {
            send = recv.clone();
            send.command = Command.DWDATA;
            send.filename = null;
            try {
                writeBytes(recv.chunkBytes);
                logger.debug("file {} write filePos {}, {} byte(s)", filename, filePos, recv.chunkBytes.length);

                filePos += recv.chunkBytes.length;
                send.filepos = filePos;
                if (filePos < fileSize) {
                    writeConf(filePos + "," + fileSize + "," + fileChksum);
                }
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
            for (; ; ) {
                confirmChunk();
                if (!recv.cmdResult) {
                    break;
                }

                send = recv.clone();
                send.command = Command.DWDATA;
                send.filepos = filePos;
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
                    if (!send.cmdResult) {
                        break;
                    }

                    recv = ClientSocket.recvPacket(s, owner.timeout);
                    logger.debug("recv {}", recv);
                }
                break;
            }

            logger.debug("file {} down over.", filename);
            result = true;
        } catch (Throwable e) {
            logger.error("file {} down failed.", filename, e);
            throw new RuntimeException("file " + filename + " down failed, send or recv Packet failed, " + e.getMessage(), e);
        } finally {
            close();
        }

    }
}
