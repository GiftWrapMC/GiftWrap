package virtuoel.kanos_plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
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

public class KanosModMetadataReader
{
	public static ModMetadataExt parseMetadata(Path modsToml)
	{
		final CommentedFileConfig file = CommentedFileConfig.of(modsToml);
		file.load();
		
		final List<CommentedConfig> mods = file.get("mods");
		
		final CommentedConfig mod = mods.get(0);
		
		final String modId = mod.get("modId");
		final String modName = mod.get("displayName");
		final String description = mod.get("description");
		final String icon = mod.get("logoFile");
		
		String version = mod.get("version");
		
		if ("${file.jarVersion}".equals(version))
		{
			try (final Stream<String> lines = Files.lines(modsToml.getParent().resolve("MANIFEST.MF")))
			{
				version = lines.filter(s -> s.startsWith("Implementation-Version"))
					.findFirst().map(s -> s.split(" ")[1]).orElse(version);
			}
			catch (IOException e)
			{
				
			}
		}
		
		final Version modVersion = Version.of(version);
		
		return new ModMetadataExt()
		{
			@Override
			public Collection<String> mixins(EnvType env)
			{
				return Collections.emptyList();
			}
			
			@Override
			public ModEnvironment environment()
			{
				return ModEnvironment.UNIVERSAL;
			}
			
			@Override
			public Collection<String> accessWideners()
			{
				return Collections.emptyList();
			}
			
			@Override
			public Version version()
			{
				return modVersion;
			}
			
			@Override
			public Map<String, LoaderValue> values()
			{
				return Collections.emptyMap();
			}
			
			@Override
			public @Nullable LoaderValue value(String key)
			{
				return null;
			}
			
			@Override
			public String name()
			{
				return modName;
			}
			
			@Override
			public Collection<ModLicense> licenses()
			{
				return Collections.emptyList();
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
				return "Contact here";
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
				return Collections.emptyList();
			}
			
			@Override
			public boolean containsValue(String key)
			{
				return false;
			}
			
			@Override
			public Map<String, String> contactInfo()
			{
				return Collections.emptyMap();
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
				return Collections.emptyMap();
			}
		};
	}
}
