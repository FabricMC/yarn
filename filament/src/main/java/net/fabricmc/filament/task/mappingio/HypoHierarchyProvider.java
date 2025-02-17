package net.fabricmc.filament.task.mappingio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.generic.HypoHydration;
import dev.denwav.hypo.model.ClassDataProvider;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.ClassKind;
import dev.denwav.hypo.model.data.FieldData;
import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.MethodDescriptor;
import dev.denwav.hypo.model.data.Visibility;
import dev.denwav.hypo.model.data.types.ArrayType;
import dev.denwav.hypo.model.data.types.JvmType;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.filament.task.mappingio.HypoHierarchyProvider.HierarchyData;
import net.fabricmc.mappingio.tree.HierarchyInfoProvider;
import net.fabricmc.mappingio.tree.MappingTreeView;

// TODO: Move this class to Mapping-IO
class HypoHierarchyProvider implements HierarchyInfoProvider<HierarchyData> {
	private final HypoContext hypo;
	private final String namespace;

	HypoHierarchyProvider(HypoContext hypoContext, String namespace) {
		this.hypo = hypoContext;
		this.namespace = namespace;
	}

	@Override
	public String getNamespace() {
		return namespace;
	}

	@Override
	@Nullable
	public String resolveField(String owner, String name, @Nullable String desc) {
		ClassData cls = getClassData(owner);
		if (cls == null) return null;

		FieldData field;

		try {
			field = resolveField(cls, name, desc);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return field != null ? field.parentClass().name() : null;
	}

	/*
	 * Based on Tiny Remapper's ClassInstance#resolveField (commit b22c17e).
	 */
	@Nullable
	private FieldData resolveField(ClassData cls, String name, @Nullable String desc) throws IOException {
		FieldData field = getFieldData(cls, name, desc);

		if (field != null) return field;

		Deque<ClassData> queue = new ArrayDeque<>();
		Set<ClassData> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		visited.add(cls);
		ClassData context = cls;

		for (;;) { // overall-recursion for fields
			// step 1
			// search in all direct super interfaces recursively

			ClassData currentCls = context;

			do {
				for (ClassData parent : currentCls.interfaces()) {
					if (visited.add(parent)) {
						FieldData ret = getFieldData(parent, name, desc);
						if (ret != null) return ret;

						queue.addLast(parent);
					}
				}
			} while ((currentCls = queue.pollLast()) != null);

			// step 2
			// search in all super classes recursively (self-lookup and queue only, outer loop will recurse)

			currentCls = context;
			context = currentCls.superClass();
			if (context == null) break;

			FieldData parentField = getFieldData(context, name, desc);
			if (parentField != null) return parentField;
		}

		return null;
	}

	@Override
	@Nullable
	public String resolveMethod(String owner, String name, @Nullable String desc) {
		ClassData cls = getClassData(owner);
		if (cls == null) return null;

		MethodData method;

		try {
			method = resolveMethod(cls, name, desc);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return method != null ? method.parentClass().name() : null;
	}

	/*
	 * Based on Tiny Remapper's ClassInstance#resolveMethod (commit b22c17e).
	 */
	@Nullable
	private MethodData resolveMethod(final ClassData cls, String name, @Nullable String desc) throws IOException {
		MethodData method = getMethodData(cls, name, desc);

		if (method != null) return method;

		// step 1
		// search in all super classes recursively

		ClassData currentCls = cls;

		while ((currentCls = currentCls.superClass()) != null) {
			MethodData ret = getMethodData(currentCls, name, desc);
			if (ret != null) return ret;
		}

		// step 2
		// search for non-static, non-private, non-abstract in all super interfaces recursively
		// (breadth first search to obtain the potentially maximally-specific superinterface directly)
		// step 3
		// bridgeMethod: search for non-static, non-private in all super interfaces recursively

		// step 3 is a super set of step 2 with any option being able to be "arbitrarily chosen" as per the jvm
		// spec, so step 2 ignoring the "exactly one" match requirement doesn't matter and >potentially<
		// maximally-specific superinterface is good enough

		Deque<ClassData> queue = new ArrayDeque<>();
		Set<ClassData> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		visited.add(cls);
		List<MethodData> matchedMethods = new ArrayList<>();
		boolean hasNonAbstract = false;
		currentCls = cls;

		do {
			List<ClassData> parents = currentCls.allSuperClasses().toList();

			for (ClassData parent : parents) {
				if (!visited.add(parent)) continue;

				if (parent.is(ClassKind.INTERFACE)) {
					MethodData parentMethod = getMethodData(parent, name, desc);

					if (parentMethod != null && !parentMethod.isStatic()) { // potential match
						if (!parentMethod.isAbstract()) hasNonAbstract = true;
						matchedMethods.add(parentMethod);
						continue; // skip queuing, subclasses aren't relevant for maximally-specific selection
					}
				}

				queue.addLast(parent);
			}
		} while ((currentCls = queue.pollFirst()) != null);

		if (hasNonAbstract && matchedMethods.size() > 1) {
			// try to select first maximally-specific superinterface bridgeMethod (doesn't matter if it's the only one, jvm spec allows arbitrary choice otherwise)
			matchLoop: for (MethodData match : matchedMethods) {
				if (match.isAbstract()) continue;

				for (MethodData m : matchedMethods) {
					if (m != match && m.parentClass().doesExtendOrImplement(match.parentClass())) continue matchLoop;
				}

				return match;
			}
		}

		if (!matchedMethods.isEmpty()) return matchedMethods.get(0);

		return null;
	}

	@Override
	@Nullable
	public HierarchyData getMethodHierarchy(String owner, String name, @Nullable String desc) {
		try {
			return getMethodHierarchy0(owner, name, desc);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/*
	 * Based on Mapping-IO's TinyRemapperHierarchyProvider#getMethodHierarchy (commit 135f1b5).
	 */
	@Nullable
	private HierarchyData getMethodHierarchy0(String owner, String name, @Nullable String desc) throws IOException {
		ClassData cls = getClassData(owner);
		if (cls == null) return null;

		MethodData method = getMethodData(cls, name, desc);
		if (method == null) return null;

		if (method.isStatic()) {
			return new HierarchyData(Collections.singleton(method));
		}

		List<MethodData> methods = new ArrayList<>();
		Queue<MethodData> toCheckUp = new ArrayDeque<>();
		Queue<MethodData> toCheckDown = new ArrayDeque<>();
		Set<MethodData> queuedUp = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<MethodData> queuedDown = Collections.newSetFromMap(new IdentityHashMap<>());
		methods.add(method);
		toCheckUp.add(method);
		toCheckDown.add(method);
		queuedUp.add(method);
		queuedDown.add(method);

		do {
			while ((method = toCheckUp.poll()) != null) {
				MethodData superMethod = method.superMethod();
				Set<MethodData> syntheticSources = method.get(HypoHydration.SYNTHETIC_SOURCES);
				Set<MethodData> upMethods = new HashSet<>();

				if (superMethod != null) {
					upMethods.add(superMethod);
				}

				if (syntheticSources != null) {
					for (MethodData syntheticSource : syntheticSources) {
						if (isPotentialBridge(syntheticSource, method)) {
							upMethods.add(syntheticSource);
						}
					}
				}

				for (MethodData upMethod : upMethods) {
					if (queuedDown.add(upMethod)) {
						methods.add(upMethod);
						toCheckDown.add(upMethod);
					}

					if (queuedUp.add(upMethod)) {
						toCheckUp.add(upMethod);
					}
				}
			}

			while ((method = toCheckDown.poll()) != null) {
				Set<MethodData> childMethods = method.childMethods();
				MethodData syntheticTarget = method.get(HypoHydration.SYNTHETIC_TARGET);
				Set<MethodData> downMethods = new HashSet<>();

				if (childMethods != null) {
					downMethods.addAll(childMethods);
				}

				if (syntheticTarget != null && isPotentialBridge(method, syntheticTarget)) {
					downMethods.add(syntheticTarget);
				}

				for (MethodData downMethod : downMethods) {
					if (queuedUp.add(downMethod)) {
						methods.add(downMethod);
						toCheckUp.add(downMethod);
					}

					if (queuedDown.add(downMethod)) {
						toCheckDown.add(downMethod);
					}
				}
			}
		} while (!toCheckUp.isEmpty() || !toCheckDown.isEmpty());

		assert methods.size() == new HashSet<>(methods).size();

		return new HierarchyData(methods);
	}

	@Override
	public int getHierarchySize(HierarchyData hierarchy) {
		return hierarchy != null ? hierarchy.methods.size() : 0;
	}

	@Override
	public Collection<? extends MappingTreeView.MethodMappingView> getHierarchyMethods(HierarchyData hierarchy, MappingTreeView tree) {
		if (hierarchy == null) return Collections.emptyList();

		List<MappingTreeView.MethodMappingView> ret = new ArrayList<>(hierarchy.methods.size());
		int ns = tree.getNamespaceId(namespace);
		assert ns != MappingTreeView.NULL_NAMESPACE_ID;

		for (MethodData method : hierarchy.methods) {
			MappingTreeView.MethodMappingView m = tree.getMethod(method.parentClass().name(), method.name(), method.descriptorText(), ns);
			if (m != null) ret.add(m);
		}

		return ret;
	}

	@Nullable
	private ClassData getClassData(String name) {
		return findClass(name, true);
	}

	@Nullable
	private FieldData getFieldData(ClassData owner, String name, String desc) {
		for (FieldData field : owner.fields()) {
			if (field.name().equals(name) && (desc == null || field.fieldType().asInternalName().equals(desc))) {
				return field;
			}
		}

		return null;
	}

	@Nullable
	private MethodData getMethodData(ClassData owner, String name, String desc) {
		for (MethodData method : owner.methods()) {
			if (method.name().equals(name) && (desc == null || method.descriptorText().equals(desc))) {
				return method;
			}
		}

		return null;
	}

	private boolean isPotentialBridge(MethodData bridgeMethod, MethodData bridgedMethod) {
		if (!bridgeMethod.isSynthetic()) return false;
		if (bridgeMethod.isBridge()) return true;

		if (bridgeMethod.visibility() == Visibility.PRIVATE || bridgeMethod.isFinal() || bridgeMethod.isStatic()) {
			return false;
		}

		MethodDescriptor bridgeDesc = bridgeMethod.descriptor();
		MethodDescriptor bridgedDesc = bridgedMethod.descriptor();
		List<JvmType> bridgeParams = bridgeDesc.getParams();
		List<JvmType> bridgedParams = bridgedDesc.getParams();

		if (bridgeParams.size() != bridgedParams.size()) {
			return false;
		}

		for (int i = 0; i < bridgeParams.size(); i++) {
			if (!areTypesBridgeCompatible(bridgeParams.get(i), bridgedParams.get(i))) {
				return false;
			}
		}

		return areTypesBridgeCompatible(bridgeDesc.getReturnType(), bridgedDesc.getReturnType());
	}

	private boolean areTypesBridgeCompatible(JvmType bridgeType, JvmType bridgedType) {
		if (bridgeType.equals(bridgedType)) {
			return true;
		}

		ClassData bridgeClass = findClass(bridgeType.asInternalName(), true);
		ClassData bridgedClass = findClass(bridgedType.asInternalName(), true);

		if (bridgeClass == null || bridgedClass == null) {
			assert bridgeType instanceof ArrayType || bridgedType instanceof ArrayType;
			return false;
		}

		boolean bridgedExtendsBridge = bridgedClass.doesExtendOrImplement(bridgeClass);

		// If not equal, types in bridge method descriptors should always be less specific than in the bridged method
		assert bridgedExtendsBridge || !bridgeClass.doesExtendOrImplement(bridgedClass);

		return bridgedExtendsBridge;
	}

	private ClassData findClass(String name, boolean allowContext) {
		ClassData cls = findClass(name, hypo.getProvider());

		if (cls != null || !allowContext) {
			return cls;
		}

		return findClass(name, hypo.getContextProvider());
	}

	private ClassData findClass(String name, ClassDataProvider provider) {
		try {
			return provider.findClass(name);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static final class HierarchyData {
		HierarchyData(Collection<MethodData> methods) {
			this.methods = methods;
		}

		final Collection<MethodData> methods;
	}
}
