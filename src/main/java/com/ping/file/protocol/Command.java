
package com.ping.file.protocol;

/**
 * 传输指令.
 *
 * @author lawnstein.chan
 * @version $Revision:$
 */
public enum Command {
    /**
     * 上传获取文件清单
     */
    UPLIST,
    /**
     * 上传确认块
     */
    UPCHUNK,
    /**
     * 上传文件
     */
    UPDATA,

    /**
     * 下载获取文件清单
     */
    DWLIST,
///**
// * 下载确认摘要
// */
//DWCHKSUM,
    /**
     * 下载确认块
     */
    DWCHUNK,
    /**
     * 下载文件
     */
    DWDATA,
}
