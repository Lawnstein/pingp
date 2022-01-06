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

import java.io.File;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 下载处理器.
 *
 * @author lawnstein.chan
 * @version $Revision:$
 */
class ClientDwlistHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientDwlistHandler.class);

    private TcpClient owner;
    private boolean localSync = false;

    private Socket s;
    private Long chunkSize;
    private String path;
    private ByteBuffer fileBytes;
    private Packet send = null;
    private Packet recv = null;
    private boolean result = false;

    public ClientDwlistHandler(TcpClient owner, boolean localSync, String path) throws Exception {
        this.owner = owner;
        this.localSync = localSync;
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

    public boolean isResult() {
        return result;
    }

    public boolean isLocalSync() {
        return localSync;
    }

    private void close() {
        logger.debug("connection {} closed for {}", s, path);
        if (s != null) {
            ClientSocket.close(s);
        }
    }

    private void doCleanUnexist(String firstDir, List<String> syncList) {
        logger.debug("Local dir {} list files, compare with syncList {} file(s)", firstDir, syncList.size());
        String basePath = Utils.getFormatedPath(Utils.getPwd()) + File.separator;
        String filePath = Utils.getCanonicalPath(basePath + firstDir + File.separator);
        List<String> l = new ArrayList<String>();
        Utils.getFiles(filePath, l);
        for (String fn : l) {
            String fnm = Utils.getFormatedPath(fn.substring(basePath.length()));
            boolean fnexist = syncList.contains(fnm);
            logger.debug("Local file {} exists ? {}", fnm, fnexist);
            if (!fnexist) {
                logger.debug("Local file removed: {}", fnm);
                Utils.fileDelete(fn);
            }
        }
    }

    private void doSyncClean(List<String> syncFiles) {
        List<String> syncList = new ArrayList<>();
        String lastDirname = null;
        for (String fn : syncFiles) {
            String d = Utils.getFirstDirname(fn);
            if (d == null || d.length() == 0) {
                continue;
            }
            if (lastDirname == null) {
                lastDirname = d;
                syncList.add(Utils.getFormatedPath(fn));
            } else if (lastDirname.equals(d)) {
                syncList.add(Utils.getFormatedPath(fn));
            } else {
                doCleanUnexist(lastDirname, syncList);

                lastDirname = d;
                syncList.clear();
                syncList.add(Utils.getFormatedPath(fn));
            }
        }
        if (lastDirname != null) {
            doCleanUnexist(lastDirname, syncList);
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
                if (recv.filepos != null && recv.filepos == Long.MAX_VALUE) {
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
                        logger.debug("dwsyn file {}/{}  {}", i, fileListArray.length, fs);
                    }
                }
                if (fileL.size() > 0) {
                    owner.fileList = new String[fileL.size()];
                    Collections.sort(fileL);
                    fileL.toArray(owner.fileList);

                    if (isLocalSync()) {
                        doSyncClean(fileL);
                    }
                }
            }

            result = true;
            logger.debug("file {} list over.", path);
        } catch (Throwable e) {
            logger.error("file {} list failed.", path, e);
            throw new RuntimeException("file " + path + " list failed, send or recv Packet failed, " + e.getMessage(), e);
        } finally {
            close();
        }

    }

}
