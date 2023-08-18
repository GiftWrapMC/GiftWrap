package virtuoel.gift_wrap;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;

import net.fabricmc.api.EnvType;

public class GiftWrapModScanner
{
	public static void scanModClasses(Path modRoot, ModMetadataExt metadata)
	{
		final Map<String, Collection<AdapterLoadableClassEntry>> entrypoints = metadata.getEntrypoints();
		final Collection<AdapterLoadableClassEntry> initEntrypoints = new ArrayList<>();
		
		final Collection<String> accessWideners = metadata.accessWideners();
		
		final Collection<String> clientMixins = metadata.mixins(EnvType.CLIENT);
		final Collection<String> serverMixins = metadata.mixins(EnvType.SERVER);
		
		final Map<String, String> modClasses = new HashMap<>();
		
		try
		{
			Files.walk(modRoot).forEach(p ->
			{
				final String fileName = modRoot.relativize(p).toString();
				
				if (!fileName.endsWith(".class"))
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
								modClasses.put(fileName, fileName.substring(0, fileName.length() - 6).replace('/', '.'));
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
	}
}
