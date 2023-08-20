package virtuoel.gift_wrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;

import net.fabricmc.api.EnvType;
import net.minecraft.item.ItemGroup;

public class GiftWrapModScanner
{
	public static void scanModClasses(Path modRoot, ModMetadataExt metadata, boolean shouldPatch)
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
				
				byte[] patchedBytes = null;
				try (final InputStream in = Files.newInputStream(p))
				{
					boolean[] patched = { false };
					ClassWriter writer = shouldPatch ? new ClassWriter(0) : null;
					ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, writer)
					{
						@Override
						public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible)
						{
							if ("Lnet/minecraftforge/fml/common/Mod;".equals(descriptor))
							{
								modClasses.put(fileName, fileName.substring(0, fileName.length() - 6).replace('/', '.'));
							}
							
							return super.visitAnnotation(descriptor, visible);
						}
						
						@Override
						public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions)
						{
							return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions))
							{
								@Override
								public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible)
								{
									return super.visitAnnotation(annotationDescriptor, visible);
								}
								
								@Override
								public void visitMethodInsn(int opcode, String owner, String insnName, String descriptor, boolean isInterface)
								{
									if (shouldPatch)
									{
										// TODO make better and only run once
										if ("builder".equals(insnName) && ItemGroup.class.getName().equals(owner.replace('/', '.')))
										{
											super.visitMethodInsn(opcode, "virtuoel/gift_wrap/hooks/ItemGroupHooks", insnName, descriptor, isInterface);
											patched[0] = true;
											return;
										}
									}
									super.visitMethodInsn(opcode, owner, insnName, descriptor, isInterface);
								}
							};
						}
					};
					
					ClassReader reader = new ClassReader(in);
					reader.accept(classVisitor, 0);
					
					if (shouldPatch && patched[0])
					{
						patchedBytes = writer.toByteArray();
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				
				if (patchedBytes != null)
				{
					try (final OutputStream out = Files.newOutputStream(p))
					{
						out.write(patchedBytes);
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
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
