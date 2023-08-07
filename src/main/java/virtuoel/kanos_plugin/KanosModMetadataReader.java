package virtuoel.kanos_plugin;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.ModContributor;
import org.quiltmc.loader.api.ModDependency;
import org.quiltmc.loader.api.ModLicense;
import org.quiltmc.loader.api.Version;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModEnvironment;

public class KanosModMetadataReader
{
	public static ModMetadataExt parseMetadata(Path modsToml)
	{
		// TODO
		
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
				return Version.of("1.0.0");
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
				return "ModName";
			}
			
			@Override
			public Collection<ModLicense> licenses()
			{
				return Collections.emptyList();
			}
			
			@Override
			public String id()
			{
				return "mod_id_here";
			}
			
			@Override
			public @Nullable String icon(int size)
			{
				return null;
			}
			
			@Override
			public String group()
			{
				return "forge_group";
			}
			
			@Override
			public @Nullable String getContactInfo(String key)
			{
				return "Contact here";
			}
			
			@Override
			public String description()
			{
				return "Desc here";
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
