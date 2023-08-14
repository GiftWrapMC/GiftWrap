package virtuoel.gift_wrap;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.gui.QuiltLoaderGui;
import org.quiltmc.loader.api.gui.QuiltLoaderIcon;
import org.quiltmc.loader.api.plugin.ModLocation;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;
import org.quiltmc.loader.api.plugin.gui.PluginGuiTreeNode;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.mappingio.format.TsrgReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public class GiftWrapPlugin implements QuiltLoaderPlugin
{
	public static final String MOD_ID = "gift_wrap";
	
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
		
		QuiltPluginManager manager = context().manager();
		
		Path fromPath = manager.getParent(root);
		
		QuiltLoaderIcon fileIcon = QuiltLoaderGui.iconJarFile();
		boolean mandatory = location.isDirect();
		boolean requiresRemap = !location.onClasspath();
		
		List<ModMetadataExt> metadata = GiftWrapModMetadataReader.parseMetadata(modsToml);
		
		ModMetadataExt meta = metadata.get(0);
		
		Path resourceRoot = root;
		Path memoryFs = manager.createMemoryFileSystem(meta.group() + ":" + meta.id());
		
		Path cache = manager.getCacheDirectory();
		
		String version = "1.20.1";
		Path tsrg = cache.resolve("forge/" + version + "/joined.tsrg");
		
		if (Files.notExists(tsrg))
		{
			LOGGER.info("Getting tsrg");
			Files.createDirectories(tsrg.getParent());
			URL url = new URL("https://raw.githubusercontent.com/neoforged/NeoForm/main/versions/release/" + version + "/joined.tsrg");
			Files.copy(url.openStream(), tsrg);
			LOGGER.info("Done");
		}
		
		MemoryMappingTree tree = new MemoryMappingTree();
		TsrgReader.read(Files.newBufferedReader(tsrg), tree);
		
		String from = "obf";
		String to = "srg";
		IMappingProvider tsrgMappings = (acceptor) -> {
			for (MappingTree.ClassMapping classDef : tree.getClasses())
			{
				String className = classDef.getName(from);
				acceptor.acceptClass(className, classDef.getName(to));
				
				for (MappingTree.FieldMapping field : classDef.getFields())
				{
					acceptor.acceptField(new IMappingProvider.Member(className, field.getName(from), field.getDesc(from)), field.getName(to));
				}
				
				for (MappingTree.MethodMapping method : classDef.getMethods())
				{
					IMappingProvider.Member methodIdentifier = new IMappingProvider.Member(className, method.getName(from), method.getDesc(from));
					acceptor.acceptMethod(methodIdentifier, method.getName(to));
				}
			}
		};
		
		TinyRemapper remapper = TinyRemapper.newRemapper()
			.withMappings(tsrgMappings)
			.renameInvalidLocals(false)
			.ignoreFieldDesc(true)
			.build();
		
		Path remappedPath = cache.resolve("forge/remapped/" + meta.id());
		if (Files.notExists(remappedPath))
		{
			Files.createDirectories(remappedPath.getParent());
			
			InputTag tag = remapper.createInputTag();
			remapper.readInputsAsync(tag, resourceRoot.toAbsolutePath());
			OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(remappedPath).build();
			outputConsumer.addNonClassFiles(resourceRoot, NonClassCopyMode.FIX_META_INF, remapper);
			remapper.apply(outputConsumer, tag);
			remapper.finish();
			outputConsumer.close();
		}
		
		Files.copy(remappedPath, memoryFs.resolve(meta.id()));
		
		ModLoadOption[] options = new ModLoadOption[metadata.size()];
		
		for (int i = 0; i < options.length; i++)
		{
			options[i] = new GiftWrapModOption(context(), metadata.get(i), fromPath, fileIcon, resourceRoot, mandatory, requiresRemap);
		}
		
		return options;
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
