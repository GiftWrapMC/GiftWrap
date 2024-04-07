package virtuoel.gift_wrap;

import java.io.IOException;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.ModContainerExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.api.plugin.solver.QuiltFileHasher;

public class GiftWrapModOption extends ModLoadOption
{
	protected final QuiltPluginContext pluginContext;
	protected final ModMetadataExt metadata;
	protected final Path from, resourceRoot;
	protected final boolean mandatory;
	protected final boolean requiresRemap;
	protected final QuiltLoaderIcon fileIcon;
	
	byte[] hash;
	
	public GiftWrapModOption(QuiltPluginContext pluginContext, ModMetadataExt meta, Path from, QuiltLoaderIcon fileIcon, Path resourceRoot, boolean mandatory, boolean requiresRemap)
	{
		this.pluginContext = pluginContext;
		this.metadata = meta;
		this.from = from;
		this.fileIcon = fileIcon;
		this.resourceRoot = resourceRoot;
		this.mandatory = mandatory;
		this.requiresRemap = requiresRemap;
	}
	
	@Override
	public ModMetadataExt metadata()
	{
		return metadata;
	}
	
	@Override
	public Path from()
	{
		return from;
	}
	
	@Override
	public QuiltLoaderIcon modFileIcon()
	{
		return fileIcon;
	}
	
	@Override
	public Path resourceRoot()
	{
		return resourceRoot;
	}
	
	@Override
	public boolean isMandatory()
	{
		return mandatory;
	}
	
	@Override
	public String toString()
	{
		return "{" + getClass().getName() + " '" + metadata.id() + "' from " + pluginContext.manager().describePath(from) + "}";
	}
	
	@Override
	public QuiltPluginContext loader()
	{
		return pluginContext;
	}
	
	@Override
	public String shortString()
	{
		return toString();
	}
	
	@Override
	public String getSpecificInfo()
	{
		return toString();
	}
	
	protected String nameOfType()
	{
		return "neoforge";
	}
	
	@Override
	public QuiltLoaderText describe()
	{
		return QuiltLoaderText.translate(
			"solver.option.mod.quilt_impl",
			nameOfType(),
			metadata.id(),
			pluginContext.manager().describePath(from)
		);
	}
	
	@Override
	public @Nullable String namespaceMappingFrom()
	{
		return null;
	}
	
	@Override
	public boolean needsTransforming()
	{
		return true;
	}
	
	@Override
	public byte[] computeOriginHash(QuiltFileHasher hasher) throws IOException
	{
		if (hash == null)
		{
			hash = hasher.computeRecursiveHash(from());
		}
		
		return hash;
	}
	
	@Override
	public QuiltLoaderIcon modTypeIcon()
	{
		return QuiltLoaderGui.iconJarFile();
	}
	
	@Override
	public ModContainerExt convertToMod(Path transformedResourceRoot)
	{
		return new GiftWrapModContainer(
			pluginContext, metadata, from, transformedResourceRoot
		);
	}
}
