package zlk.bytecodegen;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.clcalc.CcCtor;
import zlk.clcalc.CcTypeDecl;
import zlk.common.Type;
import zlk.common.id.IdMap;

/**
 * 1つのtype宣言に対応するJVMクラス群を生成する．
 */
final class CustomType {
	private static final String RUNTIME_CUSTOM_TYPE = "zlk/runtime/CustomType";
	private static final String APPEND_STRING_TO = "appendStringTo";
	private static final String APPEND_STRING_AS_ARG_TO = "appendStringAsArgTo";
	private static final String APPEND_STRING_DESC = "(Ljava/lang/StringBuilder;)V";
	private static final String APPEND_VALUE_DESC = "(Ljava/lang/StringBuilder;Ljava/lang/Object;)V";
	private static final Handle OBJECT_METHODS_BOOTSTRAP = new Handle(
			Opcodes.H_INVOKESTATIC,
			"java/lang/runtime/ObjectMethods",
			"bootstrap",
			"(Ljava/lang/invoke/MethodHandles$Lookup;"
			+ "Ljava/lang/String;"
			+ "Ljava/lang/invoke/TypeDescriptor;"
			+ "Ljava/lang/Class;"
			+ "Ljava/lang/String;"
			+ "[Ljava/lang/invoke/MethodHandle;"
			+ ")Ljava/lang/Object;",
			false);

	private final CcTypeDecl decl;
	private final String origin;
	private final String nestHost;
	private final JavaType.Simple interfaceClass;
	private final IdMap<JavaType.Variant> variantClasses;

	CustomType(String moduleName, CcTypeDecl decl, String origin) {
		this.decl = decl;
		this.origin = origin;
		this.nestHost = moduleName.replace('.', '/');
		this.interfaceClass = new JavaType.Simple(nestHost + "$" + decl.id().simpleName());
		this.variantClasses = new IdMap<>();
		decl.ctors().forEach(ctor -> variantClasses.put(
				ctor.id(),
				new JavaType.Variant(
						interfaceClass.toClassName() + "$" + ctor.id().simpleName(),
						interfaceClass.toClassName())));
	}

	void putJavaClasses(IdMap<JavaType> javaClasses, IdMap<CcCtor> ctors) {
		javaClasses.put(decl.id(), interfaceClass);
		decl.ctors().forEach(ctor -> {
			javaClasses.put(ctor.id(), variantClasses.get(ctor.id()));
			ctors.put(ctor.id(), ctor);
		});
	}

	void compile(
			int opcodeVersion,
			Function<Type, String> toDesc,
			BiConsumer<String, byte[]> fileWriter) {
		ClassWriter cw = genInterfaceClass(opcodeVersion);
		fileWriter.accept(interfaceClass.toClassName(), cw.toByteArray());

		decl.ctors().forEach(ctor -> {
			ClassWriter variantClass = genVariantClass(opcodeVersion, ctor, toDesc);
			fileWriter.accept(variantClasses.get(ctor.id()).toClassName(), variantClass.toByteArray());
		});
	}

	void registerNestMembers(ClassWriter cw) {
		cw.visitNestMember(interfaceClass.toClassName());
		decl.ctors().forEach(ctor -> cw.visitNestMember(variantClasses.get(ctor.id()).toClassName()));
	}

	/**
	 * type宣言に対応するsealed interfaceを生成する．
	 *
	 * <pre>{@code
	 * sealed interface T extends zlk.runtime.CustomType permits C1, C2 {}
	 * }</pre>
	 */
	private ClassWriter genInterfaceClass(int opcodeVersion) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(
				opcodeVersion,
				Opcodes.ACC_PUBLIC + Opcodes.ACC_INTERFACE + Opcodes.ACC_ABSTRACT,
				interfaceClass.toClassName(),
				null,
				"java/lang/Object",
				new String[] { RUNTIME_CUSTOM_TYPE });
		cw.visitSource(origin, null);
		decl.ctors().forEach(ctor ->
				cw.visitPermittedSubclass(variantClasses.get(ctor.id()).toClassName()));
		cw.visitNestHost(nestHost);
		cw.visitEnd();
		return cw;
	}

	/**
	 * Ctorに対応するrecordを生成する．
	 *
	 * <pre>{@code
	 * record C(A val0, B val1) implements T {}
	 * }</pre>
	 *
	 * 実際のclass fileには，component，field，accessor，canonical constructor，
	 * 文字列表現用メソッド，equals，hashCodeも生成する．
	 */
	private ClassWriter genVariantClass(
			int opcodeVersion,
			CcCtor ctor,
			Function<Type, String> toDesc) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		String owner = variantClasses.get(ctor.id()).toClassName();
		cw.visit(
				opcodeVersion,
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER + Opcodes.ACC_RECORD,
				owner,
				null,
				"java/lang/Record",
				new String[] { interfaceClass.toClassName() });
		cw.visitSource(origin, null);

		for(int i = 0; i < ctor.args().size(); i++) {
			String componentName = componentName(i);
			String componentDesc = toDesc.apply(ctor.args().at(i));
			cw.visitRecordComponent(componentName, componentDesc, null).visitEnd();
			cw.visitField(
					Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL,
					componentName,
					componentDesc,
					null,
					null).visitEnd();
			genComponentAccessor(cw, owner, componentName, componentDesc);
		}

		genCanonicalConstructor(cw, owner, ctor, toDesc);
		genToString(cw, owner);
		genAppendStringTo(cw, owner, ctor, toDesc);
		genAppendStringAsArgTo(cw, owner, ctor);
		genEqualsAndHashCode(cw, owner, ctor, toDesc);
		cw.visitNestHost(nestHost);
		cw.visitEnd();
		return cw;
	}

	/**
	 * recordのcanonical constructorを生成する．
	 *
	 * <pre>{@code
	 * C(A val0, B val1) {
	 *     this.val0 = val0;
	 *     this.val1 = val1;
	 * }
	 * }</pre>
	 */
	private void genCanonicalConstructor(
			ClassWriter cw,
			String owner,
			CcCtor ctor,
			Function<Type, String> toDesc) {
		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC,
				"<init>",
				constructorDesc(ctor, toDesc),
				null,
				null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL,
				"java/lang/Record",
				"<init>",
				"()V",
				false);

		for(int i = 0; i < ctor.args().size(); i++) {
			String componentDesc = toDesc.apply(ctor.args().at(i));
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ALOAD, i + 1);
			mv.visitFieldInsn(
					Opcodes.PUTFIELD,
					owner,
					componentName(i),
					componentDesc);
		}

		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/**
	 * record componentのaccessorを生成する．
	 *
	 * <pre>{@code
	 * public A val0() {
	 *     return val0;
	 * }
	 * }</pre>
	 */
	private void genComponentAccessor(
			ClassWriter cw,
			String owner,
			String componentName,
			String componentDesc) {
		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC,
				componentName,
				"()" + componentDesc,
				null,
				null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, owner, componentName, componentDesc);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/**
	 * appendStringToへ委譲するtoStringを生成する．
	 *
	 * <pre>{@code
	 * public final String toString() {
	 *     StringBuilder sb = new StringBuilder();
	 *     appendStringTo(sb);
	 *     return sb.toString();
	 * }
	 * }</pre>
	 */
	private void genToString(ClassWriter cw, String owner) {
		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
				"toString",
				"()Ljava/lang/String;",
				null,
				null);
		mv.visitCode();
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(
				Opcodes.INVOKESPECIAL,
				"java/lang/StringBuilder",
				"<init>",
				"()V",
				false);
		mv.visitVarInsn(Opcodes.ASTORE, 1);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				owner,
				APPEND_STRING_TO,
				APPEND_STRING_DESC,
				false);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/lang/StringBuilder",
				"toString",
				"()Ljava/lang/String;",
				false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/**
	 * constructor名とcomponentを通常位置の表記で追加するsyntheticメソッドを生成する．
	 *
	 * <pre>{@code
	 * public final void appendStringTo(StringBuilder sb) {
	 *     sb.append("C");
	 *     sb.append(" ");
	 *     CustomType.appendStringAsArgTo(sb, val0);
	 * }
	 * }</pre>
	 */
	private void genAppendStringTo(
			ClassWriter cw,
			String owner,
			CcCtor ctor,
			Function<Type, String> toDesc) {
		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SYNTHETIC,
				APPEND_STRING_TO,
				APPEND_STRING_DESC,
				null,
				null);
		mv.visitCode();
		appendConstant(mv, ctor.id().simpleName());

		for(int i = 0; i < ctor.args().size(); i++) {
			appendConstant(mv, " ");
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(
					Opcodes.GETFIELD,
					owner,
					componentName(i),
					toDesc.apply(ctor.args().at(i)));
			mv.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					RUNTIME_CUSTOM_TYPE,
					APPEND_STRING_AS_ARG_TO,
					APPEND_VALUE_DESC,
					true);
		}

		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/**
	 * constructorを引数位置の表記で追加するsyntheticメソッドを生成する．
	 *
	 * <pre>{@code
	 * public final void appendStringAsArgTo(StringBuilder sb) {
	 *     sb.append("(");       // componentがある場合だけ生成
	 *     appendStringTo(sb);
	 *     sb.append(")");       // componentがある場合だけ生成
	 * }
	 * }</pre>
	 */
	private void genAppendStringAsArgTo(ClassWriter cw, String owner, CcCtor ctor) {
		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SYNTHETIC,
				APPEND_STRING_AS_ARG_TO,
				APPEND_STRING_DESC,
				null,
				null);
		mv.visitCode();
		if(!ctor.args().isEmpty()) {
			appendConstant(mv, "(");
		}
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				owner,
				APPEND_STRING_TO,
				APPEND_STRING_DESC,
				false);
		if(!ctor.args().isEmpty()) {
			appendConstant(mv, ")");
		}
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private void appendConstant(MethodVisitor mv, String value) {
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitLdcInsn(value);
		mv.visitMethodInsn(
				Opcodes.INVOKEVIRTUAL,
				"java/lang/StringBuilder",
				"append",
				"(Ljava/lang/String;)Ljava/lang/StringBuilder;",
				false);
		mv.visitInsn(Opcodes.POP);
	}

	/**
	 * record標準と同じequalsおよびhashCodeを生成する．
	 *
	 * <pre>{@code
	 * record C(A val0, B val1) implements T {}
	 * }</pre>
	 *
	 * 上記recordにjavacが生成するメソッドと同様に，ObjectMethods.bootstrapへ委譲する．
	 */
	private void genEqualsAndHashCode(
			ClassWriter cw,
			String owner,
			CcCtor ctor,
			Function<Type, String> toDesc) {
		Object[] bootstrapArgs = objectMethodBootstrapArgs(ctor, owner, toDesc);

		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
				"hashCode",
				"()I",
				null,
				null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitInvokeDynamicInsn(
				"hashCode",
				"(L" + owner + ";)I",
				OBJECT_METHODS_BOOTSTRAP,
				bootstrapArgs);
		mv.visitInsn(Opcodes.IRETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
				"equals",
				"(Ljava/lang/Object;)Z",
				null,
				null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitInvokeDynamicInsn(
				"equals",
				"(L" + owner + ";Ljava/lang/Object;)Z",
				OBJECT_METHODS_BOOTSTRAP,
				bootstrapArgs);
		mv.visitInsn(Opcodes.IRETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private Object[] objectMethodBootstrapArgs(
			CcCtor ctor,
			String owner,
			Function<Type, String> toDesc) {
		Object[] args = new Object[ctor.args().size() + 2];
		args[0] = org.objectweb.asm.Type.getObjectType(owner);

		StringBuilder componentNames = new StringBuilder();
		for(int i = 0; i < ctor.args().size(); i++) {
			if(i != 0) {
				componentNames.append(';');
			}
			String componentName = componentName(i);
			String componentDesc = toDesc.apply(ctor.args().at(i));
			componentNames.append(componentName);
			args[i + 2] = new Handle(
					Opcodes.H_GETFIELD,
					owner,
					componentName,
					componentDesc,
					false);
		}
		args[1] = componentNames.toString();
		return args;
	}

	private String constructorDesc(CcCtor ctor, Function<Type, String> toDesc) {
		StringBuilder sb = new StringBuilder("(");
		ctor.args().forEach(type -> sb.append(toDesc.apply(type)));
		return sb.append(")V").toString();
	}

	static String componentName(int index) {
		return "val" + index;
	}
}
