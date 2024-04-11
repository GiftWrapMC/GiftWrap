package io.github.giftwrapmc.gift_wrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModEntrypoint;

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
		final Collection<ModLicense> licenses = Collections.singletonList(ModLicense.fromIdentifierOrDefault(license));
		
		final String[] manifestData = { null, null };
		
		try (final Stream<String> lines = Files.lines(modsToml.getParent().resolve("MANIFEST.MF")))
		{
			lines.forEach(line ->
			{
				if (line.startsWith("Implementation-Version"))
				{
					final int index = line.indexOf(' ');
					if (index != -1)
					{
						manifestData[0] = line.substring(index + 1);
					}
				}
				else if (line.startsWith("MixinConfigs"))
				{
					final int index = line.indexOf(' ');
					if (index != -1)
					{
						manifestData[1] = line.substring(index + 1);
					}
				}
			});
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		final Set<String> mixinConfigs = new HashSet<>();
		Optional.<List<CommentedConfig>>ofNullable(file.get("mixins")).ifPresent(mixins ->
		{
			for (final CommentedConfig mixinJson : mixins)
			{
				mixinConfigs.add(mixinJson.get("config"));
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
			
			final String versionString = mod.get("version");
			final Version modVersion = Version.of("${file.jarVersion}".equals(versionString) ? manifestData[0] : versionString);
			
			final Map<String, LoaderValue> customValues = new HashMap<>();
			customValues.put("patchwork:patcherMeta", new TomlLoaderValue.BooleanImpl(VALUE_LOCATION, true));
			final Map<String, TomlLoaderValue> modmenu = new HashMap<>();
			modmenu.put("update_checker", new TomlLoaderValue.BooleanImpl(VALUE_LOCATION, false));
			customValues.put("modmenu", new TomlLoaderValue.ObjectImpl(VALUE_LOCATION, modmenu));
			
			final Map<String, Collection<ModEntrypoint>> entrypoints = new HashMap<>();
			
			final Collection<String> accessWideners = new ArrayList<>();
			
			final Collection<String> mixins = new HashSet<>();
			mixins.addAll(mixinConfigs);
			mixinConfigs.clear();
			
			if (manifestData[1] != null)
			{
				mixins.add(manifestData[1]);
			}
			
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
					return "loader.neoforge";
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
				public Map<String, Collection<ModEntrypoint>> getEntrypoints()
				{
					return entrypoints;
				}
			});
		}
		
		return metadata;
	}
}
