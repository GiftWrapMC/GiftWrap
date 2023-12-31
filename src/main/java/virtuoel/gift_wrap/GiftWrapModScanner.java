package virtuoel.gift_wrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodInsnNode;
import org.quiltmc.loader.api.plugin.ModMetadataExt;
import org.quiltmc.loader.api.plugin.ModMetadataExt.ModEntrypoint;

public class GiftWrapModScanner
{
	public static final Map<String, String> SHADOWED_FIELD_NAMES = new HashMap<>();
	public static final Map<String, String> SHADOWED_METHOD_NAMES = new HashMap<>();
	public static final Collection<BiPredicate<String, MethodInsnNode>> METHOD_INSN_PATCHES = new ArrayList<>();
	
	public static void scanModClasses(Path modRoot, ModMetadataExt metadata, boolean shouldPatch)
	{
		final String accessWidener = metadata.id() + ".accesswidener";
		final Path accessWidenerPath = modRoot.resolve(accessWidener);
		if (Files.exists(accessWidenerPath))
		{
			metadata.accessWideners().add(accessWidener);
		}
		
		final Map<String, Collection<ModEntrypoint>> entrypoints = metadata.getEntrypoints();
		final Collection<ModEntrypoint> initEntrypoints = new ArrayList<>();
		
		final Set<String> modClasses = new LinkedHashSet<>();
		final Set<String> mixinClasses = new LinkedHashSet<>();
		
		try
		{
			Files.walk(modRoot).forEach(p ->
			{
				final String fileName = modRoot.relativize(p).toString();
				
				if (!fileName.endsWith(".class"))
				{
					return;
				}
				
				final String className = fileName.substring(0, fileName.length() - 6).replace('/', '.');
				
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
							if ("Lnet/neoforged/fml/common/Mod;".equals(descriptor))
							{
								modClasses.add(className);
							}
							
							if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor))
							{
								mixinClasses.add(className);
							}
							
							return super.visitAnnotation(descriptor, visible);
						}
						
						@Override
						public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value)
						{
							if (shouldPatch)
							{
								if (mixinClasses.contains(className))
								{
									if (name.length() > 3 && name.startsWith("f_") && name.endsWith("_") && SHADOWED_FIELD_NAMES.containsKey(name))
									{
										patched[0] = true;
										name = SHADOWED_FIELD_NAMES.get(name);
									}
								}
							}
							
							return super.visitField(access, name, descriptor, signature, value);
						}
						
						@Override
						public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions)
						{
							if (shouldPatch)
							{
								if (mixinClasses.contains(className))
								{
									if (name.length() > 3 && name.startsWith("m_") && name.endsWith("_") && SHADOWED_METHOD_NAMES.containsKey(name))
									{
										patched[0] = true;
										name = SHADOWED_METHOD_NAMES.get(name);
									}
								}
							}
							
							return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions))
							{
								@Override
								public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible)
								{
									return super.visitAnnotation(annotationDescriptor, visible);
								}
								
								@Override
								public void visitFieldInsn(int opcode, String owner, String insnName, String descriptor)
								{
									if (shouldPatch)
									{
										if (mixinClasses.contains(className))
										{
											if (insnName.length() > 3 && insnName.startsWith("f_") && insnName.endsWith("_") && SHADOWED_FIELD_NAMES.containsKey(insnName))
											{
												patched[0] = true;
												insnName = SHADOWED_FIELD_NAMES.get(insnName);
											}
										}
									}
									
									super.visitFieldInsn(opcode, owner, insnName, descriptor);
								}
								
								@Override
								public void visitMethodInsn(int opcode, String owner, String insnName, String descriptor, boolean isInterface)
								{
									if (shouldPatch)
									{
										if (mixinClasses.contains(className))
										{
											if (insnName.length() > 3 && insnName.startsWith("m_") && insnName.endsWith("_") && SHADOWED_METHOD_NAMES.containsKey(insnName))
											{
												patched[0] = true;
												insnName = SHADOWED_METHOD_NAMES.get(insnName);
											}
										}
										
										final MethodInsnNode node = new MethodInsnNode(opcode, owner, insnName, descriptor, isInterface);
										
										for (final BiPredicate<String, MethodInsnNode> patch : METHOD_INSN_PATCHES)
										{
											if (patch.test(className, node))
											{
												patched[0] = true;
												break;
											}
										}
										
										super.visitMethodInsn(node.getOpcode(), node.owner, node.name, node.desc, node.itf);
									}
									else
									{
										super.visitMethodInsn(opcode, owner, insnName, descriptor, isInterface);
									}
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
		
		for (final String modClass : modClasses)
		{
			initEntrypoints.add(ModEntrypoint.create("gift_wrap", modClass));
		}
		
		entrypoints.put("init", initEntrypoints);
	}
}
