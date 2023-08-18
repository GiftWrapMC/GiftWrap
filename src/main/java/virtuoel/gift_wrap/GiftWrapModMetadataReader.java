package virtuoel.gift_wrap;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModEnvironment;

public class GiftWrapModMetadataReader
{
	private static final String VALUE_LOCATION = "not-from-file";
	
	public static List<ModMetadataExt> parseMetadata(Path modsToml)
	{
		final CommentedFileConfig file = CommentedFileConfig.of(modsToml);
		file.load();
		
		final String license = file.get("license");
		final Collection<ModLicense> licenses = Collections.singletonList(new ModLicense()
		{
			@Override
			public String url()
			{
				return "";
			}
			
			@Override
			public String name()
			{
				return license;
			}
			
			@Override
			public String id()
			{
				return license;
			}
			
			@Override
			public String description()
			{
				return "";
			}
		});
		
		final String issueTrackerUrl = file.get("issueTrackerURL");
		
		final List<CommentedConfig> mods = file.get("mods");
		final List<ModMetadataExt> metadata = new ArrayList<>();
		
		for (final CommentedConfig mod : mods)
		{
			final String modId = mod.get("modId");
			final String modName = mod.get("displayName");
			final String description = mod.get("description");
			final String icon = mod.get("logoFile");
			
			final Map<String, String> contactInfo = new HashMap<>();
			contactInfo.put("homepage", mod.get("displayURL"));
			contactInfo.put("issues", issueTrackerUrl);
			
			final String authors = mod.get("authors");
			final Collection<ModContributor> contributors = authors == null ? Collections.emptyList() : Collections.singletonList(ModContributor.of(authors, Collections.singletonList("Author")));
			
			String version = mod.get("version");
			
			if ("${file.jarVersion}".equals(version))
			{
				try (final Stream<String> lines = Files.lines(modsToml.getParent().resolve("MANIFEST.MF")))
				{
					version = lines.filter(s -> s.startsWith("Implementation-Version"))
						.findFirst()
						.map(s ->
						{
							final int index = s.indexOf(' ');
							return index == -1 ? null : s.substring(index + 1);
						})
						.orElse(version);
				}
				catch (IOException e)
				{
					
				}
			}
			
			final Version modVersion = Version.of(version);
			
			final Map<String, LoaderValue> customValues = new HashMap<>();
			customValues.put("patchwork:patcherMeta", new TomlLoaderValue.BooleanImpl(VALUE_LOCATION, true));
			final Map<String, TomlLoaderValue> modmenu = new HashMap<>();
			modmenu.put("update_checker", new TomlLoaderValue.BooleanImpl(VALUE_LOCATION, false));
			customValues.put("modmenu", new TomlLoaderValue.ObjectImpl(VALUE_LOCATION, modmenu));
			
			final Map<String, String> modClasses = new HashMap<>();
			
			try
			{
				Files.walk(modsToml.getParent().getParent()).forEach(p ->
				{
					final String name = p.toString();
					
					if (!name.endsWith(".class"))
					{
						return;
					}
					
					try (final InputStream in = Files.newInputStream(p))
					{
						ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9)
						{
							@Override
							public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible)
							{
								if ("Lnet/minecraftforge/fml/common/Mod;".equals(descriptor))
								{
									modClasses.put(name, name.substring(1, name.length() - 6).replace('/', '.'));
								}
								
								return null;
							}
						};
						new ClassReader(in).accept(classVisitor, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				});
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			
			final Map<String, Collection<AdapterLoadableClassEntry>> entrypoints = new HashMap<>();
			final Collection<AdapterLoadableClassEntry> initEntrypoints = new ArrayList<>();
			
			for (final String modClass : modClasses.values())
			{
				try
				{
					initEntrypoints.add(AdapterLoadableClassEntry.class.getConstructor(String.class, String.class).newInstance("gift_wrap", modClass));
				}
				catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e)
				{
					e.printStackTrace();
				}
			}
			
			entrypoints.put("init", initEntrypoints);
			
			final Collection<String> accessWideners = new ArrayList<>();
			// TODO
			
			final Collection<String> mixins = new ArrayList<>();
			// TODO
			
			metadata.add(new ModMetadataExt()
			{
				@Override
				public Collection<String> mixins(EnvType env)
				{
					return mixins;
				}
				
				@Override
				public ModEnvironment environment()
				{
					return ModEnvironment.UNIVERSAL;
				}
				
				@Override
				public Collection<String> accessWideners()
				{
					return accessWideners;
				}
				
				@Override
				public Version version()
				{
					return modVersion;
				}
				
				@Override
				public Map<String, LoaderValue> values()
				{
					return customValues;
				}
				
				@Override
				public @Nullable LoaderValue value(String key)
				{
					return values().get(key);
				}
				
				@Override
				public String name()
				{
					return modName;
				}
				
				@Override
				public Collection<ModLicense> licenses()
				{
					return licenses;
				}
				
				@Override
				public String id()
				{
					return modId;
				}
				
				@Override
				public @Nullable String icon(int size)
				{
					return icon;
				}
				
				@Override
				public String group()
				{
					return "loader.forge";
				}
				
				@Override
				public @Nullable String getContactInfo(String key)
				{
					return contactInfo().getOrDefault(key, null);
				}
				
				@Override
				public String description()
				{
					return description;
				}
				
				@Override
				public Collection<ModDependency> depends()
				{
					return Collections.emptyList();
				}
				
				@Override
				public Collection<ModContributor> contributors()
				{
					return contributors;
				}
				
				@Override
				public boolean containsValue(String key)
				{
					return values().containsKey(key);
				}
				
				@Override
				public Map<String, String> contactInfo()
				{
					return contactInfo;
				}
				
				@Override
				public Collection<ModDependency> breaks()
				{
					return Collections.emptyList();
				}
				
				@Override
				public @Nullable ModPlugin plugin()
				{
					return null;
				}
				
				@Override
				public Map<String, String> languageAdapters()
				{
					return Collections.emptyMap();
				}
				
				@Override
				public Map<String, Collection<AdapterLoadableClassEntry>> getEntrypoints()
				{
					return entrypoints;
				}
			});
		}
		
		return metadata;
	}
}
