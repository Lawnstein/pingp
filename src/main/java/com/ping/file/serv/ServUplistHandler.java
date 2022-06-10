/**
 * Copyright 2005-2021 Client Service International, Inc. All rights reserved. <br>
 * CSII PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.<br>
 * <br>
 * project: pingp <br>
 * create: 2021年11月28日 下午1:41:19 <br>
 * vc: $Id: $
 */

package com.ping.file.serv;

import com.ping.file.protocol.Command;
import com.ping.file.protocol.Packet;
import com.ping.file.util.ClientSocket;
import com.ping.file.util.Utils;
import com.ping.sync.ChangeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 上传文件清单处理器.
 *
 * @author lawnstein.chan
 * @version $Revision:$
 */
class ServUplistHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

    private TcpServer owner;

    private Socket s;
    private Long chunkSize;
    private String filename = null;
    //    private String filePath = null;
    private ByteBuffer fileBytes = null;
    private long fileSize = 0l;
    private long fileCount = 0l;
    private long filePos = 0l;
    //	private long fileChunkIndex = 0l;
    private Packet recv = null;
    private Packet send = new Packet();
    private boolean handleOver = false;
//    private String[] files = null;

    public ServUplistHandler(ServFilter filter) {
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

    private void doCleanUnexist(String firstDir, List<String> syncList) {
        if (Utils.isEmpty(firstDir)) {
            return;
        }

        String basePath = ChangeManager.getBaseDir();
        String filePath = Utils.getCanonicalPath(basePath + firstDir + File.separator);
        logger.debug("Serv localDir {}({},{}) list files, compare with syncList {} file(s) : {}", firstDir, basePath, filePath, syncList.size(), syncList);
        List<String> l = new ArrayList<String>();
        Utils.getFiles(filePath, true, l);
        Collections.sort(l);
        Collections.reverse(l);
        logger.debug("Serv localFiles : {}", l);
        for (String fn : l) {
            if (fn.endsWith(Utils.DEFAULT_TRANSFERING_CNF_SUFFIX)) {
                continue;
            }

            String fnm = Utils.getFormatedPath(fn.substring(basePath.length()));
            boolean fnexist = syncList.contains(fnm);
            logger.debug("Serv localFile {} exists ? {}", fnm, fnexist);
            if (!fnexist) {
                logger.info("Serv file removed: {}", fnm);
                ChangeManager.deleteServChangelog(fn.substring(basePath.length()));
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
            for (; ; ) {

                /**
                 * recv the file list.
                 */
                // recv = ClientSocket.recvPacket(s, owner.timeout);
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
                    send.filepos = 0l;
                    send.command = Command.UPLIST;
                    ClientSocket.sendPacket(s, send);
                    logger.debug("send {}", send);

                    recv = ClientSocket.recvPacket(s, owner.timeout);
                    logger.debug("recv {}", recv);
                }

                String[] files = null;
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
                            logger.debug("up file {}/{}  {}", i, fileListArray.length, fs);
                        }
                    }
                    if (fileL.size() > 0) {
                        files = new String[fileL.size()];
                        Collections.sort(fileL);
                        Collections.reverse(fileL);
                        fileL.toArray(files);

                        doSyncClean(fileL);
                    }
                }

                send = recv.clone();
                send.filepos = Long.MAX_VALUE;
                ClientSocket.sendPacket(s, send);
                logger.debug("send {}", send);

                logger.debug("uplist {} over.", filename);
                break;
            }
        } catch (Throwable th) {
            if (owner.isDebug()) {
                logger.error("uplist {} failed, {}", filename, th);
            } else {
                logger.error("uplist {} failed, {}", filename, th.getMessage());
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