package net.fabricmc.filament.enigma.annotations.editor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public enum ClassDeclType {
	CLASS("class"),
	INTERFACE("interface"),
	ENUM("enum"),
	ANNOTATION("@interface"),
	RECORD("record");

	private final String keyword;

	ClassDeclType(String keyword) {
		this.keyword = keyword;
	}

	public String getKeyword() {
		return keyword;
	}

	public boolean isInterface() {
		return this == INTERFACE || this == ANNOTATION;
	}

	public static ClassDeclType infer(ClassNode classNode) {
		if ((classNode.access & Opcodes.ACC_RECORD) != 0) {
			return RECORD;
		}

		if ((classNode.access & Opcodes.ACC_ENUM) != 0) {
			return ENUM;
		}

		if ((classNode.access & Opcodes.ACC_ANNOTATION) != 0) {
			return ANNOTATION;
		}

		if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
			return INTERFACE;
		}

		return CLASS;
	}
}
