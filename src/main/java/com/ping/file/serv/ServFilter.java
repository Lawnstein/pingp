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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;

/**
 * 主过滤器/分流器.
 *
 * @author lawnstein.chan
 * @version $Revision:$
 */
class ServFilter implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

    protected TcpServer owner;

    protected Socket s;
    protected Packet recv = null;

    public ServFilter(TcpServer owner, Socket socket) {
        this.owner = owner;
        this.s = socket;
        logger.info("connection {} accepted", s);
    }

    private void close() {
        logger.info("connection {} closed", s);
        if (s != null) {
            ClientSocket.close(s);
        }
    }

    @Override
    public void run() {
        try {
            recv = ClientSocket.recvPacket(s, owner.timeout);
            logger.debug("Recv [{}], {}", recv, s);

            Runnable r = null;
            if (Command.UPLIST.equals(recv.command)) {
				r = new ServUplistHandler(this);
            } else if (Command.UPCHUNK.equals(recv.command)) {
                r = new ServUpfileHandler(this);
            } else if (Command.DWLIST.equals(recv.command)) {
                r = new ServDwlistHandler(this);
            } else if (Command.DWCHUNK.equals(recv.command)) {
                r = new ServDwfileHandler(this);
            } else {
                throw new RuntimeException("unexpected first handle type.");
            }

            r.run();
        } catch (Throwable th) {
            if (owner.isDebug()) {
                logger.error("connection filter failed, {}", th);
            } else {
                logger.error("connection filter failed, {}", th.getMessage());
            }
            close();
        } finally {
        }
    }

}