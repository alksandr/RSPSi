package com.jagex.net;

import com.rspsi.cache.CacheFileType;
import lombok.Getter;

@Getter
public class ResourceRequest {
	
	private int group;
	private int file;
	private CacheFileType type;
	private long requestTime;
	
	public ResourceRequest(int group, CacheFileType type) {
		this(group, 0, type);
	}

	public ResourceRequest(int group, int file, CacheFileType type) {
		this.group = group;
		this.file = file;
		this.type = type;
		this.requestTime = System.currentTimeMillis();
	}

	public long getAge() {
		return System.currentTimeMillis() - requestTime;
	}

}