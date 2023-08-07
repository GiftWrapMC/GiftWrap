package virtuoel.kanos_plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.plugin.ModLocation;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KanosPlugin implements QuiltLoaderPlugin
{
	public static final String MOD_ID = "kanos_plugin";
	
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	private QuiltPluginContext context;
	
	@Override
	public ModLoadOption[] scanZip(Path root, ModLocation location, PluginGuiTreeNode guiNode) throws IOException
	{
		Path modsToml = root.resolve("META-INF/mods.toml");
		if (!FasterFiles.isRegularFile(modsToml))
		{
			return null;
		}
		LOGGER.info("Loaded a mod");
		
		Path from = context().manager().getParent(root);
		
		QuiltLoaderIcon fileIcon = QuiltLoaderGui.iconJarFile();
		boolean mandatory = location.isDirect();
		ModMetadataExt meta = KanosModMetadataReader.parseMetadata(modsToml);
		boolean requiresRemap = !location.onClasspath();
		return new ModLoadOption[] { new KanosModOption(context(), meta, from, fileIcon, root, mandatory, requiresRemap) };
	}
	
	public QuiltPluginContext context()
	{
		return context;
	}
	
	@Override
	public void load(QuiltPluginContext context, Map<String, LoaderValue> previousData)
	{
		this.context = context;
	}
	
	@Override
	public void unload(Map<String, LoaderValue> data)
	{
		
	}
}
