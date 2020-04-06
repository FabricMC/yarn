package net.fabricmc.mappingpoet;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ModifierBuilder {

	private final int access;

	public ModifierBuilder(int access) {
		this.access = access;
	}

	public Modifier[] getModifiers(Type type) {
		List<Modifier> modifiers = new ArrayList<>();

		if (type == Type.PARAM) {
			if (java.lang.reflect.Modifier.isFinal(access)) {
				modifiers.add(Modifier.FINAL);
			}
			return modifiers.toArray(new Modifier[]{});
		}

		if (java.lang.reflect.Modifier.isPublic(access)) {
			modifiers.add(Modifier.PUBLIC);
		} else if (java.lang.reflect.Modifier.isPrivate(access)) {
			modifiers.add(Modifier.PRIVATE);
		} else if (java.lang.reflect.Modifier.isProtected(access)) {
			modifiers.add(Modifier.PROTECTED);
		}

		if (java.lang.reflect.Modifier.isAbstract(access)) {
			modifiers.add(Modifier.ABSTRACT);
		}
		if (java.lang.reflect.Modifier.isStatic(access)) {
			modifiers.add(Modifier.STATIC);
		}
		if (java.lang.reflect.Modifier.isFinal(access)) {
			modifiers.add(Modifier.FINAL);
		}
		if (java.lang.reflect.Modifier.isTransient(access) && type == Type.FIELD) {
			modifiers.add(Modifier.TRANSIENT);
		}
		if (java.lang.reflect.Modifier.isVolatile(access) && type == Type.FIELD) {
			modifiers.add(Modifier.VOLATILE);
		}
		if (java.lang.reflect.Modifier.isSynchronized(access) && type == Type.METHOD) {
			modifiers.add(Modifier.SYNCHRONIZED);
		}
		if (java.lang.reflect.Modifier.isNative(access) && type == Type.METHOD) {
			modifiers.add(Modifier.NATIVE);
		}
		if (java.lang.reflect.Modifier.isStrict(access)) {
			modifiers.add(Modifier.STRICTFP);
		}

		return modifiers.toArray(new Modifier[]{});
	}

	public enum Type {
		CLASS,
		METHOD,
		FIELD,
		PARAM
	}
}
