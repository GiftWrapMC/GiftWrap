package virtuoel.gift_wrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipError;

import org.objectweb.asm.tree.MethodInsnNode;
import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.QuiltLoader;
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
import net.fabricmc.mappingio.format.ProGuardReader;
import net.fabricmc.mappingio.format.Tiny2Reader;
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
	
	private String version;
	
	private QuiltPluginContext context;
	private MemoryMappingTree mappingTree;
	private IMappingProvider mappingProvider;
	private Path memoryFileSystem;
	
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
		
		Path cache = manager.getCacheDirectory();
		
		Path remappedMinecraft = cache.resolve(MOD_ID + "/remapped/minecraft");
		remapVanillaIfNeeded(remappedMinecraft);
		
		Path remappedPath = cache.resolve(MOD_ID + "/remapped/" + meta.id());
		boolean wasRemapped = remapModIfNeeded(meta, resourceRoot, remappedMinecraft, remappedPath);
		
		GiftWrapModScanner.scanModClasses(remappedPath, meta, wasRemapped);
		
		Files.copy(resourceRoot = remappedPath, this.memoryFileSystem.resolve(meta.id()));
		
		ModLoadOption[] options = new ModLoadOption[metadata.size()];
		
		for (int i = 0; i < options.length; i++)
		{
			options[i] = new GiftWrapModOption(context(), metadata.get(i), fromPath, fileIcon, resourceRoot, mandatory, requiresRemap);
		}
		
		return options;
	}
	
	public boolean remapVanillaIfNeeded(final Path remappedMinecraft) throws IOException
	{
		if (Files.notExists(remappedMinecraft))
		{
			Path minecraftRoot = context().manager().getAllMods("minecraft").stream().findFirst().get().resourceRoot();
			
			boolean development = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));
			String src = development ? "named" : "intermediary";
			
			TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(createMappingProvider(mappingTree(), src, "mojang"))
				.renameInvalidLocals(false)
				.ignoreFieldDesc(true)
				.ignoreConflicts(true)
				.build();
			
			LOGGER.info("Remapping vanilla...");
			
			remapper.getEnvironment();
			
			Files.createDirectories(remappedMinecraft.getParent());
			
			InputTag tag = remapper.createInputTag();
			remapper.readInputsAsync(tag, minecraftRoot.toAbsolutePath());
			OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(remappedMinecraft).build();
			outputConsumer.addNonClassFiles(minecraftRoot, NonClassCopyMode.FIX_META_INF, remapper);
			remapper.apply(outputConsumer, tag);
			remapper.finish();
			outputConsumer.close();
			
			LOGGER.info("Done remapping vanilla.");
			
			return true;
		}
		
		return false;
	}
	
	public boolean remapModIfNeeded(final ModMetadataExt meta, final Path resourceRoot, final Path remappedMinecraft, final Path remappedPath) throws IOException
	{
		if (Files.notExists(remappedPath))
		{
			TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(mappingProvider())
				.renameInvalidLocals(false)
				.ignoreFieldDesc(true)
				.ignoreConflicts(true)
				.build();
			
			LOGGER.info("Remapping new or changed mod \"{}\"...", meta.id());
			
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
			
			Path accessTransformerPath = remappedPath.resolve("META-INF").resolve("accesstransformer.cfg");
			if (Files.exists(accessTransformerPath))
			{
				Path accessWidenerPath = remappedPath.resolve(meta.id() + ".accesswidener");
				if (Files.notExists(accessWidenerPath))
				{
					try (final OutputStream out = Files.newOutputStream(accessWidenerPath))
					{
						Map<String, Boolean> deferredClasses = new HashMap<>();
						Map<String, Map<String, Boolean>> deferredMethods = new HashMap<>();
						Map<String, Map<String, Boolean>> deferredFields = new HashMap<>();
						try (final Stream<String> lines = Files.lines(accessTransformerPath))
						{
							lines.forEach(line ->
							{
								line = line.trim();
								if (line.startsWith("#"))
								{
									return;
								}
								
								String[] parts = line.split(" ");
								
								String visibility = parts[0];
								String clazz = parts[1].replace('.', '/');
								String desc = parts.length > 2 ? parts[2] : null;
								
								boolean unfinal = visibility.endsWith("-f");
								
								if (desc == null || desc.startsWith("#"))
								{
									deferredClasses.put(clazz, unfinal);
								}
								else
								{
									int index = desc.indexOf('(');
									if (index != -1)
									{
										deferredMethods.computeIfAbsent(clazz, $ -> new HashMap<>()).put(desc.substring(0, index), unfinal);
									}
									else
									{
										deferredFields.computeIfAbsent(clazz, $ -> new HashMap<>()).put(desc, unfinal);
									}
								}
							});
						}
						
						String dst = "intermediary";
						
						StringBuilder text = new StringBuilder("accessWidener\tv2\t");
						text.append(dst);
						
						Boolean unfinal;
						String srcClass, clazz, name, desc;
						Map<String, Boolean> entries;
						for (MappingTree.ClassMapping c : mappingTree().getClasses())
						{
							srcClass = c.getName("mojang");
							clazz = c.getName(dst);
							
							unfinal = deferredClasses.get(srcClass);
							if (unfinal != null)
							{
								text.append('\n');
								text.append(unfinal ? "extendable" : "accessible");
								text.append("\tclass\t");
								text.append(clazz);
							}
							
							entries = deferredMethods.get(srcClass);
							if (entries != null)
							{
								for (MappingTree.MethodMapping m : c.getMethods())
								{
									unfinal = entries.get(m.getName("mojang"));
									if (unfinal != null)
									{
										text.append('\n');
										text.append("accessible");
										text.append("\tmethod\t");
										text.append(clazz);
										text.append('\t');
										text.append(name = m.getName(dst));
										text.append('\t');
										text.append(desc = m.getDesc(dst));
										if (unfinal)
										{
											text.append('\n');
											text.append("extendable");
											text.append("\tmethod\t");
											text.append(clazz);
											text.append('\t');
											text.append(name);
											text.append('\t');
											text.append(desc);
										}
									}
								}
							}
							
							entries = deferredFields.get(srcClass);
							if (entries != null)
							{
								for (MappingTree.FieldMapping f : c.getFields())
								{
									unfinal = entries.get(f.getName("mojang"));
									if (unfinal != null)
									{
										text.append('\n');
										text.append("accessible");
										text.append("\tfield\t");
										text.append(clazz);
										text.append('\t');
										text.append(name = f.getName(dst));
										text.append('\t');
										text.append(desc = f.getDesc(dst));
										if (unfinal)
										{
											text.append('\n');
											text.append("mutable");
											text.append("\tfield\t");
											text.append(clazz);
											text.append('\t');
											text.append(name);
											text.append('\t');
											text.append(desc);
										}
									}
								}
							}
						}
						
						out.write(text.toString().getBytes(StandardCharsets.UTF_8));
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}
			}
			
			LOGGER.info("Done remapping mod \"{}\".", meta.id());
			
			return true;
		}
		
		return false;
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
	
	public static IMappingProvider createMappingProvider(MemoryMappingTree tree, String src, String dst)
	{
		return (acceptor) ->
		{
			for (MappingTree.ClassMapping classDef : tree.getClasses())
			{
				String className = classDef.getName(src);
				String dstName = classDef.getName(dst);
				if (className == null || dstName == null) continue;
				acceptor.acceptClass(className, dstName);
				
				String srcName;
				for (MappingTree.FieldMapping field : classDef.getFields())
				{
					srcName = field.getName(src);
					dstName = field.getName(dst);
					if (srcName == null || dstName == null) continue;
					acceptor.acceptField(new IMappingProvider.Member(className, srcName, field.getDesc(src)), dstName);
				}
				
				for (MappingTree.MethodMapping method : classDef.getMethods())
				{
					srcName = method.getName(src);
					dstName = method.getName(dst);
					if (srcName == null || dstName == null) continue;
					acceptor.acceptMethod(new IMappingProvider.Member(className, srcName, method.getDesc(src)), dstName);
				}
			}
		};
	}
	
	public QuiltPluginContext context()
	{
		return context;
	}
	
	public IMappingProvider mappingProvider() throws IOException
	{
		if (mappingProvider != null)
		{
			return mappingProvider;
		}
		
		boolean development = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));
		String dst = development ? "named" : "intermediary";
		mappingProvider = createMappingProvider(mappingTree(), "mojang", dst);
		
		return mappingProvider;
	}
	
	public MemoryMappingTree mappingTree() throws IOException
	{
		if (mappingTree != null)
		{
			return mappingTree;
		}
		
		LOGGER.info("Loading mappings on first access...");
		
		Path clientMappings = this.memoryFileSystem.resolve(MOD_ID + "/" + version + "/client.txt");
		
		if (Files.notExists(clientMappings))
		{
			LOGGER.info("Getting client.txt");
			Files.createDirectories(clientMappings.getParent());
			URL url = new URL("https://piston-data.mojang.com/v1/objects/be76ecc174ea25580bdc9bf335481a5192d9f3b7/client.txt");
			Files.copy(url.openStream(), clientMappings);
			LOGGER.info("Done");
		}
		
		Path serverMappings = this.memoryFileSystem.resolve(MOD_ID + "/" + version + "/server.txt");
		
		if (Files.notExists(serverMappings))
		{
			LOGGER.info("Getting server.txt");
			Files.createDirectories(serverMappings.getParent());
			URL url = new URL("https://piston-data.mojang.com/v1/objects/c1cafe916dd8b58ed1fe0564fc8f786885224e62/server.txt");
			Files.copy(url.openStream(), serverMappings);
			LOGGER.info("Done");
		}
		
		mappingTree = new MemoryMappingTree();
		
		ProGuardReader.read(Files.newBufferedReader(clientMappings), "mojang", "official", mappingTree);
		ProGuardReader.read(Files.newBufferedReader(serverMappings), "mojang", "official", mappingTree);
		loadIntermediary(mappingTree);
		
		LOGGER.info("Done loading mappings.");
		
		return mappingTree;
	}
	
	@Override
	public void load(QuiltPluginContext context, Map<String, LoaderValue> previousData)
	{
		this.context = context;
		
		this.version = context().manager().getAllMods("minecraft").stream().findFirst().get().version().toString();
		
		this.memoryFileSystem = context.manager().createMemoryFileSystem(MOD_ID);
		
		QuiltLoader.getObjectShare().put("gift_wrap:method_insn_patches", (Consumer<BiPredicate<String, MethodInsnNode>>) GiftWrapModScanner.METHOD_INSN_PATCHES::add);
	}
	
	@Override
	public void unload(Map<String, LoaderValue> data)
	{
		QuiltLoader.getObjectShare().remove("gift_wrap:method_insn_patches");
		GiftWrapModScanner.METHOD_INSN_PATCHES.clear();
	}
}
