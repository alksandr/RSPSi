package com.rspsi.gallifrey;

import com.displee.cache.index.Index;
import com.displee.cache.index.archive.Archive;
import java.util.Optional;
import com.jagex.Client;
import com.jagex.cache.loader.anim.AnimationDefinitionLoader;
import com.jagex.cache.loader.anim.FrameBaseLoader;
import com.jagex.cache.loader.anim.FrameLoader;
import com.jagex.cache.loader.anim.GraphicLoader;
import com.jagex.cache.loader.config.RSAreaLoader;
import com.jagex.cache.loader.config.VariableBitLoader;
import com.jagex.cache.loader.floor.FloorDefinitionLoader;
import com.jagex.cache.loader.map.MapIndexLoader;
import com.jagex.cache.loader.object.ObjectDefinitionLoader;
import com.jagex.cache.loader.textures.TextureLoader;
import com.jagex.net.ResourceResponse;
import com.rspsi.cache.CacheFileType;
import com.rspsi.gallifrey.loader.AnimationDefinitionLoaderOSRS;
import com.rspsi.gallifrey.loader.FloorDefinitionLoaderOSRS;
import com.rspsi.gallifrey.loader.FrameBaseLoaderOSRS;
import com.rspsi.gallifrey.loader.FrameLoaderOSRS;
import com.rspsi.gallifrey.loader.GallifreyRawCache;
import com.rspsi.gallifrey.loader.GraphicLoaderOSRS;
import com.rspsi.gallifrey.loader.MapIndexLoaderOSRS;
import com.rspsi.gallifrey.loader.ObjectDefinitionLoaderOSRS;
import com.rspsi.gallifrey.loader.RSAreaLoaderOSRS;
import com.rspsi.gallifrey.loader.TextureLoaderOSRS;
import com.rspsi.gallifrey.loader.VarbitLoaderOSRS;
import com.rspsi.plugins.core.ClientPlugin;

public class GallifreyPlugin implements ClientPlugin {

    private FrameLoaderOSRS frameLoader;
    private FloorDefinitionLoaderOSRS floorLoader;
    private ObjectDefinitionLoaderOSRS objLoader;
    private AnimationDefinitionLoaderOSRS animDefLoader;
    private GraphicLoaderOSRS graphicLoader;
    private VarbitLoaderOSRS varbitLoader;
    private MapIndexLoaderOSRS mapIndexLoader;
    private TextureLoaderOSRS textureLoader;
    private FrameBaseLoaderOSRS skeletonLoader;
    private RSAreaLoaderOSRS areaLoader;

    @Override
    public void initializePlugin() {
        objLoader = new ObjectDefinitionLoaderOSRS();
        floorLoader = new FloorDefinitionLoaderOSRS();
        frameLoader = new FrameLoaderOSRS();
        animDefLoader = new AnimationDefinitionLoaderOSRS();

        mapIndexLoader = new MapIndexLoaderOSRS();
        textureLoader = new TextureLoaderOSRS();
        skeletonLoader = new FrameBaseLoaderOSRS();
        graphicLoader = new GraphicLoaderOSRS();
        varbitLoader = new VarbitLoaderOSRS();
        areaLoader = new RSAreaLoaderOSRS();

        MapIndexLoader.instance = mapIndexLoader;
        GraphicLoader.instance = graphicLoader;
        VariableBitLoader.instance = varbitLoader;
        FrameLoader.instance = frameLoader;
        ObjectDefinitionLoader.instance = objLoader;
        FloorDefinitionLoader.instance = floorLoader;
        FrameBaseLoader.instance = skeletonLoader;
        TextureLoader.instance = textureLoader;
        AnimationDefinitionLoader.instance = animDefLoader;
        RSAreaLoader.instance = areaLoader;
    }

    @Override
    public void onGameLoaded(final Client client) {
        frameLoader.init(2500);

        Index configIndex = client.getCache().getFile(CacheFileType.CONFIG);

        floorLoader.initUnderlays(configIndex.archive(1));
        floorLoader.initOverlays(configIndex.archive(4));

        // Objects (archive 6) crash displee's multi-file split on this cache (duplicate file
        // ids + non-standard reference table), so read it positionally straight from disk.
        String cacheDir = client.getCache().getIndexedFileSystem().getPath();
        objLoader.init(GallifreyRawCache.readConfigArchive(cacheDir, 6));
        animDefLoader.init(configIndex.archive(12));
        graphicLoader.init(configIndex.archive(13));
        varbitLoader.init(configIndex.archive(14));
        areaLoader.init(configIndex.archive(35));

        objLoader.renameMapFunctions(areaLoader);

        Index skeletonIndex = client.getCache().getFile(CacheFileType.SKELETON);
        skeletonLoader.init(skeletonIndex);

        Index textureIndex = client.getCache().getFile(CacheFileType.TEXTURE);
        Index spriteIndex = client.getCache().getFile(CacheFileType.SPRITE);
        textureLoader.init(textureIndex.archive(0), spriteIndex);

        // Maps are name-keyed ("m{x}_{z}"/"l{x}_{z}") separate archives, each a single file.
        // Resolve names to archive ids, and always read file 0 (the core requests object maps
        // with file index 1, which only applies to caches that pack land+objects in one archive).
        final Index mapIndex = client.getCache().getFile(CacheFileType.MAP);
        mapIndexLoader.init(mapIndex);
        client.getCache().setMapRetrieverOverride((groupId, regionId) -> {
            try {
                Archive archive = mapIndex.archive(groupId);
                if (archive == null || archive.file(0) == null || archive.file(0).getData() == null) {
                    return Optional.empty();
                }
                return Optional.of(archive.file(0).getData());
            } catch (Exception ex) {
                return Optional.empty();
            }
        });
    }

    @Override
    public void onResourceDelivered(ResourceResponse arg0) {
        // TODO Auto-generated method stub

    }

}
