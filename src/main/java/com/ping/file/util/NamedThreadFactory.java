
package com.ping.file.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 命名线程工厂.
 * 
 * @author Lawnstein.Chan
 * @version $Revision:$
 */
public class NamedThreadFactory implements ThreadFactory {
	private final static Map<String, AtomicInteger> threadNumber = new ConcurrentHashMap<String, AtomicInteger>();

	private String namePrefix = "";

	public NamedThreadFactory(String namePrefix) {
		this.namePrefix = namePrefix;
	}

	@Override
	public Thread newThread(Runnable runnable) {
		AtomicInteger ai = threadNumber.get(namePrefix);
		if (ai == null) {
			synchronized (threadNumber) {
				ai = threadNumber.get(namePrefix);
				if (ai == null) {
					ai = new AtomicInteger(1);
					threadNumber.put(namePrefix, ai);
				}
			}
		}
		String n = this.namePrefix + "-" + ai.getAndIncrement();
		Thread t = new Thread(runnable, n);
		if (t.isDaemon()) {
			t.setDaemon(false);
		}
		if (t.getPriority() != Thread.NORM_PRIORITY) {
			t.setPriority(Thread.NORM_PRIORITY);
		}
		return t;
	}
}
