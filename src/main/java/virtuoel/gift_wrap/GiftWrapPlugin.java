package virtuoel.gift_wrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipError;

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
import org.quiltmc.loader.impl.launch.common.MappingConfiguration;
import org.quiltmc.loader.impl.util.SystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.format.ProGuardReader;
import net.fabricmc.mappingio.format.Tiny2Reader;
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
	private IMappingProvider mappings;
	
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
		
		Path remappedMinecraft = cache.resolve("forge/remapped/minecraft");
		if (Files.notExists(remappedMinecraft))
		{
			Path minecraftRoot = manager.getAllMods("minecraft").stream().findFirst().get().resourceRoot();
			
			boolean development = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));
			String src = development ? "named" : "intermediary";
			
			TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(createMappingProvider(tree("1.20.1"), src, "mojang", src, "srg"))
				.renameInvalidLocals(false)
				.ignoreFieldDesc(true)
				.ignoreConflicts(true)
				.build();
			
			remapper.getEnvironment();
			
			Files.createDirectories(remappedMinecraft.getParent());
			
			InputTag tag = remapper.createInputTag();
			remapper.readInputsAsync(tag, minecraftRoot.toAbsolutePath());
			OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(remappedMinecraft).build();
			outputConsumer.addNonClassFiles(minecraftRoot, NonClassCopyMode.FIX_META_INF, remapper);
			remapper.apply(outputConsumer, tag);
			remapper.finish();
			outputConsumer.close();
		}
		
		Path remappedPath = cache.resolve("forge/remapped/" + meta.id());
		if (Files.notExists(remappedPath))
		{
			TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(mappings("1.20.1"))
				.renameInvalidLocals(false)
				.ignoreFieldDesc(true)
				.ignoreConflicts(true)
				.build();
			
			remapper.readClassPath(remappedMinecraft);
			
			remapper.getEnvironment();
			
			Files.createDirectories(remappedPath.getParent());
			
			InputTag tag = remapper.createInputTag();
			remapper.readInputsAsync(tag, resourceRoot.toAbsolutePath());
			OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(remappedPath).build();
			outputConsumer.addNonClassFiles(resourceRoot, NonClassCopyMode.FIX_META_INF, remapper);
			remapper.apply(outputConsumer, tag);
			remapper.finish();
			outputConsumer.close();
		}
		
		Files.copy(resourceRoot = remappedPath, memoryFs.resolve(meta.id()));
		
		ModLoadOption[] options = new ModLoadOption[metadata.size()];
		
		for (int i = 0; i < options.length; i++)
		{
			options[i] = new GiftWrapModOption(context(), metadata.get(i), fromPath, fileIcon, resourceRoot, mandatory, requiresRemap);
		}
		
		return options;
	}
	
	public void loadIntermediary(final MappingVisitor visitor)
	{
		final Enumeration<URL> urls;
		try
		{
			urls = MappingConfiguration.class.getClassLoader().getResources("mappings/mappings.tiny");
		}
		catch (IOException e)
		{
			throw new UncheckedIOException("Error trying to locate mappings", e);
		}
		
		while (urls.hasMoreElements())
		{
			final URL url = urls.nextElement();
			try
			{
				final URLConnection connection = url.openConnection();
				
				try (final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream())))
				{
					Tiny2Reader.read(reader, visitor);
				}
			}
			catch (IOException | ZipError e)
			{
				throw new RuntimeException("Error reading " + url, e);
			}
		}
	}
	
	public static IMappingProvider createMappingProvider(MemoryMappingTree tree, String classSrc, String classDst, String src, String dst)
	{
		return (acceptor) ->
		{
			for (MappingTree.ClassMapping classDef : tree.getClasses())
			{
				String className = classDef.getName(classSrc);
				String dstName = classDef.getName(classDst);
				if (className == null || dstName == null) continue;
				acceptor.acceptClass(className, dstName);
				
				String srcName;
				for (MappingTree.FieldMapping field : classDef.getFields())
				{
					srcName = field.getName(src);
					dstName = field.getName(dst);
					if (srcName == null || dstName == null) continue;
					acceptor.acceptField(new IMappingProvider.Member(className, srcName, field.getDesc(classSrc)), dstName);
				}
				
				for (MappingTree.MethodMapping method : classDef.getMethods())
				{
					srcName = method.getName(src);
					dstName = method.getName(dst);
					if (srcName == null || dstName == null) continue;
					acceptor.acceptMethod(new IMappingProvider.Member(className, srcName, method.getDesc(classSrc)), dstName);
				}
			}
		};
	}
	
	public QuiltPluginContext context()
	{
		return context;
	}
	
	public IMappingProvider mappings(String version) throws IOException
	{
		if (mappings == null)
		{
			boolean development = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));
			String dst = development ? "named" : "intermediary";
			mappings = createMappingProvider(tree(version), "mojang", dst, "srg", dst);
		}
		
		return mappings;
	}
	
	public MemoryMappingTree tree(String version) throws IOException
	{
		Path cache = context().manager().getCacheDirectory();
		Path tsrg = cache.resolve("forge/" + version + "/joined.tsrg");
		
		if (Files.notExists(tsrg))
		{
			LOGGER.info("Getting tsrg");
			Files.createDirectories(tsrg.getParent());
			URL url = new URL("https://raw.githubusercontent.com/neoforged/NeoForm/main/versions/release/" + version + "/joined.tsrg");
			Files.copy(url.openStream(), tsrg);
			LOGGER.info("Done");
		}
		
		Path clientMappings = cache.resolve("forge/" + version + "/client.txt");
		
		if (Files.notExists(clientMappings))
		{
			LOGGER.info("Getting client.txt");
			Files.createDirectories(clientMappings.getParent());
			URL url = new URL("https://piston-data.mojang.com/v1/objects/6c48521eed01fe2e8ecdadbd5ae348415f3c47da/client.txt");
			Files.copy(url.openStream(), clientMappings);
			LOGGER.info("Done");
		}
		
		Path serverMappings = cache.resolve("forge/" + version + "/server.txt");
		
		if (Files.notExists(serverMappings))
		{
			LOGGER.info("Getting server.txt");
			Files.createDirectories(serverMappings.getParent());
			URL url = new URL("https://piston-data.mojang.com/v1/objects/0b4dba049482496c507b2387a73a913230ebbd76/server.txt");
			Files.copy(url.openStream(), serverMappings);
			LOGGER.info("Done");
		}
		
		MemoryMappingTree tree = new MemoryMappingTree();
		
		ProGuardReader.read(Files.newBufferedReader(clientMappings), "mojang", "official", tree);
		ProGuardReader.read(Files.newBufferedReader(serverMappings), "mojang", "official", tree);
		TsrgReader.read(Files.newBufferedReader(tsrg), new MappingNsRenamer(tree, Map.of("obf", "official")));
		loadIntermediary(tree);
		
		return tree;
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
