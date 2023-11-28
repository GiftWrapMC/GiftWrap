package virtuoel.gift_wrap;

import java.nio.file.Path;
import java.util.List;

import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;

public class GiftWrapModContainer implements ModContainerExt
{
	private final String pluginId;
	private final ModMetadataExt metadata;
	private final Path resourceRoot;
	private final List<List<Path>> sourcePaths;
	
	public GiftWrapModContainer(QuiltPluginContext pluginContext, ModMetadataExt metadata, Path from, Path resourceRoot)
	{
		this.pluginId = pluginContext.pluginId();
		this.metadata = metadata;
		this.resourceRoot = resourceRoot;
		
		this.sourcePaths = pluginContext.manager().convertToSourcePaths(from);
	}
	
	@Override
	public String pluginId()
	{
		return pluginId;
	}
	
	@Override
	public ModMetadataExt metadata()
	{
		return metadata;
	}
	
	@Override
	public Path rootPath()
	{
		return resourceRoot;
	}
	
	@Override
	public List<List<Path>> getSourcePaths()
	{
		return sourcePaths;
	}
	
	@Override
	public boolean shouldAddToQuiltClasspath()
	{
		return true;
	}
	
	@Override
	public BasicSourceType getSourceType()
	{
		return BasicSourceType.OTHER;
	}
	
	@Override
	public String modType()
	{
		return "NeoForge";
	}
}
