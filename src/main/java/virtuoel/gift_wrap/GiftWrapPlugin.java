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
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

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
import virtuoel.gift_wrap.hooks.StaticMethodPatcher;

public class GiftWrapPlugin implements QuiltLoaderPlugin
{
	public static final String MOD_ID = "gift_wrap";
	
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	private String version;
	
	private QuiltPluginContext context;
	private MemoryMappingTree mappingTree;
	private IMappingProvider mappingProvider;
	
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
		
		Path remappedMinecraft = cache.resolve(MOD_ID + "/remapped/minecraft");
		if (Files.notExists(remappedMinecraft))
		{
			Path minecraftRoot = manager.getAllMods("minecraft").stream().findFirst().get().resourceRoot();
			
			boolean development = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));
			String src = development ? "named" : "intermediary";
			
			TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(createMappingProvider(mappingTree(), src, "mojang", src, "srg"))
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
		}
		
		Path remappedPath = cache.resolve(MOD_ID + "/remapped/" + meta.id());
		boolean firstScan = Files.notExists(remappedPath);
		if (firstScan)
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
						String srgClass, clazz, name, desc;
						Map<String, Boolean> entries;
						for (MappingTree.ClassMapping c : mappingTree().getClasses())
						{
							srgClass = c.getName("mojang");
							clazz = c.getName(dst);
							
							unfinal = deferredClasses.get(srgClass);
							if (unfinal != null)
							{
								text.append('\n');
								text.append(unfinal ? "extendable" : "accessible");
								text.append("\tclass\t");
								text.append(clazz);
							}
							
							entries = deferredMethods.get(srgClass);
							if (entries != null)
							{
								for (MappingTree.MethodMapping m : c.getMethods())
								{
									unfinal = entries.get(m.getName("srg"));
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
							
							entries = deferredFields.get(srgClass);
							if (entries != null)
							{
								for (MappingTree.FieldMapping f : c.getFields())
								{
									unfinal = entries.get(f.getName("srg"));
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
			
			meta.mixins(null).stream().findFirst().map(remappedPath::resolve).ifPresent(path ->
			{
				try
				{
					Path refmapPath = remappedPath.resolve(JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("refmap").getAsString());
					JsonObject refmap = JsonParser.parseString(Files.readString(refmapPath)).getAsJsonObject();
					
					JsonObject mappedData = new JsonObject();
					JsonObject data = refmap.get("data").getAsJsonObject();
					JsonObject srgData = data.get("searge").getAsJsonObject();
					data.add("named:intermediary", mappedData);
					refmap.add("mappings", mappedData);
					
					Map<String[], JsonObject> deferredMethods = new HashMap<>();
					Map<String[], JsonObject> deferredFields = new HashMap<>();
					String toRemap;
					JsonObject mapped, mappings;
					for (String mixinClass : srgData.keySet())
					{
						mapped = new JsonObject();
						mappings = srgData.get(mixinClass).getAsJsonObject();
						for (final Entry<String, JsonElement> entry : mappings.entrySet())
						{
							toRemap = entry.getValue().getAsString();
							
							boolean isField = toRemap.contains(":");
							int nameIndex = toRemap.startsWith("L") ? toRemap.indexOf(';') + 1 : 0;
							int descIndex = toRemap.indexOf(isField ? ':' : '(');
							
							String clazz = nameIndex == 0 ? null : toRemap.substring(1, nameIndex - 1);
							String name = toRemap.substring(nameIndex, descIndex);
							String desc = toRemap.substring(descIndex + (isField ? 1 : 0));
							
							(isField ? deferredFields : deferredMethods).put(new String[] { entry.getKey(), clazz, name, desc }, mapped);
						}
						mappedData.add(mixinClass, mapped);
					}
					
					String dst = "intermediary";
					String srgClass, srgMethod, srgField, clazz, dstName;
					String[] found;
					boolean populateShadowedMethods = GiftWrapModScanner.SHADOWED_METHOD_NAMES.isEmpty();
					boolean populateShadowedFields = GiftWrapModScanner.SHADOWED_FIELD_NAMES.isEmpty();
					for (MappingTree.ClassMapping c : mappingTree().getClasses())
					{
						srgClass = c.getName("mojang");
						clazz = c.getName(dst);
						
						for (MappingTree.MethodMapping m : c.getMethods())
						{
							srgMethod = m.getName("srg");
							dstName = m.getName(dst);
							if (populateShadowedMethods)
							{
								if (dstName != null && srgMethod.length() > 3 && srgMethod.startsWith("m_") && srgMethod.endsWith("_"))
								{
									GiftWrapModScanner.SHADOWED_METHOD_NAMES.put(srgMethod, dstName);
								}
							}
							
							found = null;
							for (String[] method : deferredMethods.keySet())
							{
								if (method[1] != null && method[1].equals(srgClass))
								{
									method[1] = clazz;
								}
								
								if (srgMethod.equals(method[2]) && m.getDesc("mojang").equals(method[3]))
								{
									if (dstName != null)
									{
										method[2] = dstName;
										method[3] = m.getDesc(dst);
										found = method;
										break;
									}
								}
							}
							
							if (found != null)
							{
								deferredMethods.remove(found).addProperty(found[0], (found[1] == null ? "" : ("L" + found[1] + ";")) + found[2] + found[3]);
							}
						}
						
						for (MappingTree.FieldMapping f : c.getFields())
						{
							srgField = f.getName("srg");
							dstName = f.getName(dst);
							if (populateShadowedFields)
							{
								if (dstName != null && srgField.length() > 3 && srgField.startsWith("f_") && srgField.endsWith("_"))
								{
									GiftWrapModScanner.SHADOWED_FIELD_NAMES.put(srgField, dstName);
								}
							}
							
							found = null;
							for (String[] field : deferredFields.keySet())
							{
								if (field[1] != null && field[1].equals(srgClass))
								{
									field[1] = clazz;
								}
								
								if (srgField.equals(field[2]) && f.getDesc("mojang").equals(field[3]))
								{
									if (dstName != null)
									{
										field[2] = dstName;
										field[3] = f.getDesc(dst);
										found = field;
										break;
									}
								}
							}
							
							if (found != null)
							{
								deferredFields.remove(found).addProperty(found[0], (found[1] == null ? "" : ("L" + found[1] + ";")) + found[2] + ":" + found[3]);
							}
						}
					}
					
					try (OutputStream out = Files.newOutputStream(refmapPath))
					{
						Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
						
						out.write(gson.toJson(refmap).getBytes(StandardCharsets.UTF_8));
					}
				}
				catch (JsonSyntaxException | IOException e)
				{
					e.printStackTrace();
				}
			});
			
			LOGGER.info("Done remapping mod \"{}\".", meta.id());
		}
		
		GiftWrapModScanner.scanModClasses(remappedPath, meta, firstScan);
		
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
	
	public IMappingProvider mappingProvider() throws IOException
	{
		if (mappingProvider != null)
		{
			return mappingProvider;
		}
		
		boolean development = Boolean.parseBoolean(System.getProperty(SystemProperties.DEVELOPMENT, "false"));
		String dst = development ? "named" : "intermediary";
		mappingProvider = createMappingProvider(mappingTree(), "mojang", dst, "srg", dst);
		
		return mappingProvider;
	}
	
	public MemoryMappingTree mappingTree() throws IOException
	{
		if (mappingTree != null)
		{
			return mappingTree;
		}
		
		LOGGER.info("Loading mappings on first access...");
		
		Path cache = context().manager().getCacheDirectory();
		Path tsrg = cache.resolve(MOD_ID + "/" + version + "/joined.tsrg");
		
		if (Files.notExists(tsrg))
		{
			LOGGER.info("Getting tsrg");
			Files.createDirectories(tsrg.getParent());
			URL url = new URL("https://raw.githubusercontent.com/neoforged/NeoForm/main/versions/release/" + version + "/joined.tsrg");
			Files.copy(url.openStream(), tsrg);
			LOGGER.info("Done");
		}
		
		Path clientMappings = cache.resolve(MOD_ID + "/" + version + "/client.txt");
		
		if (Files.notExists(clientMappings))
		{
			LOGGER.info("Getting client.txt");
			Files.createDirectories(clientMappings.getParent());
			URL url = new URL("https://piston-data.mojang.com/v1/objects/6c48521eed01fe2e8ecdadbd5ae348415f3c47da/client.txt");
			Files.copy(url.openStream(), clientMappings);
			LOGGER.info("Done");
		}
		
		Path serverMappings = cache.resolve(MOD_ID + "/" + version + "/server.txt");
		
		if (Files.notExists(serverMappings))
		{
			LOGGER.info("Getting server.txt");
			Files.createDirectories(serverMappings.getParent());
			URL url = new URL("https://piston-data.mojang.com/v1/objects/0b4dba049482496c507b2387a73a913230ebbd76/server.txt");
			Files.copy(url.openStream(), serverMappings);
			LOGGER.info("Done");
		}
		
		mappingTree = new MemoryMappingTree();
		
		ProGuardReader.read(Files.newBufferedReader(clientMappings), "mojang", "official", mappingTree);
		ProGuardReader.read(Files.newBufferedReader(serverMappings), "mojang", "official", mappingTree);
		TsrgReader.read(Files.newBufferedReader(tsrg), new MappingNsRenamer(mappingTree, Map.of("obf", "official")));
		loadIntermediary(mappingTree);
		
		LOGGER.info("Done loading mappings.");
		
		return mappingTree;
	}
	
	@Override
	public void load(QuiltPluginContext context, Map<String, LoaderValue> previousData)
	{
		this.context = context;
		
		this.version = context().manager().getAllMods("minecraft").stream().findFirst().get().version().toString();
		
		QuiltLoader.getObjectShare().put("gift_wrap:method_insn_patches", (Consumer<Predicate<MethodInsnNode>>) GiftWrapModScanner.METHOD_INSN_PATCHES::add);
		
		// TODO move with hooks to API plugin
		QuiltLoader.getObjectShare().whenAvailable("gift_wrap:method_insn_patches", (key, value) ->
		{
			@SuppressWarnings("unchecked")
			final Consumer<Predicate<MethodInsnNode>> patches = (Consumer<Predicate<MethodInsnNode>>) value;
			patches.accept(StaticMethodPatcher::patch);
		});
	}
	
	@Override
	public void unload(Map<String, LoaderValue> data)
	{
		QuiltLoader.getObjectShare().remove("gift_wrap:method_insn_patches");
		GiftWrapModScanner.METHOD_INSN_PATCHES.clear();
	}
}
