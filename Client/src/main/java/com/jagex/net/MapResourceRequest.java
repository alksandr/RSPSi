package com.jagex.net;

import com.rspsi.cache.CacheFileType;

import lombok.Getter;

@Getter
public class MapResourceRequest extends ResourceRequest {
	private int regionId;

	public MapResourceRequest(int regionId, int group, int file) {
		super(group, file, CacheFileType.MAP);
		this.regionId = regionId;
	}
}