package net.fabricmc.filament.enigma.annotations.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import cuchaz.enigma.api.view.entry.ClassEntryView;
import cuchaz.enigma.api.view.entry.FieldEntryView;
import cuchaz.enigma.api.view.entry.MethodEntryView;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.filament.enigma.annotations.AnnotationsEnigmaPlugin;
import net.fabricmc.filament.enigma.annotations.AnnotationsIndex;

public class AnnotationParser {
	private final AnnotationsEnigmaPlugin plugin;
	private final int caret;
	private final List<ParseError> errors = new ArrayList<>();
	private final List<CompletionOption> completions = new ArrayList<>();

	public AnnotationParser(AnnotationsEnigmaPlugin plugin, int caret) {
		this.plugin = plugin;
		this.caret = caret;
	}

	public AnnotationNode parse(String source, Function<String, AnnotationNode> annotationCreator, Predicate<ClassNode> isAnnotationAllowed) {
		Cursor c = new Cursor(source);
		c.skipWhitespace();

		AnnotationNode res = parseAnnotationInstance(c, annotationCreator, isAnnotationAllowed);

		if (res == null) {
			return null;
		}

		c.skipWhitespace();

		if (!c.eof()) {
			c.addErrorRange(c.index, source.length(), "Trailing characters after annotation");
		}

		return res;
	}

	private AnnotationNode parseAnnotationInstance(Cursor c, Function<String, AnnotationNode> annotationCreator, Predicate<ClassNode> isAnnotationAllowed) {
		if (!c.expect('@')) {
			c.addErrorRange(c.index, c.index, "Expected annotation");
			return null;
		}

		c.addCompletions(() -> {
			AnnotationsIndex index;

			try {
				index = plugin.index.join();
			} catch (Exception e) {
				return List.of();
			}

			return index.annotations().stream()
					.filter(annotation -> {
						ClassNode cn = plugin.project.getBytecode(annotation);
						return cn != null && isAnnotationAllowed.test(cn);
					})
					.map(annotation -> {
						String deobf = plugin.project.deobfuscate(ClassEntryView.create(annotation)).getFullName();
						String canonical = deobf.replace('/', '.').replace('$', '.');
						int dotIndex = canonical.lastIndexOf('.');
						return new CompletionOption(canonical.substring(dotIndex + 1), canonical, dotIndex == -1 ? null : canonical.substring(0, dotIndex));
					})
					.toList();
		});

		int nameStart = c.index;
		String canonical = c.parseQualifiedName();

		if (canonical == null) {
			c.addErrorRange(nameStart, c.index, "Expected annotation class name");
			return null;
		}

		int nameEnd = c.index;

		String internal = resolveToInternalName(canonical);
		ClassNode annotationClass = internal != null ? plugin.project.getBytecode(plugin.project.obfuscate(ClassEntryView.create(internal)).getFullName()) : null;

		if (internal == null) {
			internal = canonical.replace('.', '/');
			c.addErrorRange(nameStart, nameEnd, "Annotation class not found: " + canonical);
		}

		if (annotationClass != null && (annotationClass.access & Opcodes.ACC_ANNOTATION) == 0) {
			c.addErrorRange(nameStart, nameEnd, "Type is not an annotation: " + canonical);
		}

		if (annotationClass != null && !isAnnotationAllowed.test(annotationClass)) {
			c.addErrorRange(nameStart, nameEnd, "This annotation is not allowed here");
		}

		String desc = "L" + internal + ";";
		AnnotationNode node = annotationCreator.apply(desc);

		c.skipWhitespace();

		if (c.peek() != '(') {
			return node;
		}

		c.consume();
		c.skipWhitespace();

		if (annotationClass != null) {
			completeAnnotationAttributes(c, annotationClass, node);
		}

		if (c.peek() == ')') {
			c.consume();
			return node;
		}

		// determine form
		int save = c.index;
		String maybeIdent = c.parseIdentifier();
		c.skipWhitespace();
		boolean isNamed = maybeIdent != null && c.peek() == '=';
		c.index = save;

		if (isNamed) {
			boolean firstAttribute = true;

			while (true) {
				if (!firstAttribute) {
					c.skipWhitespace();

					if (c.peek() == ',') {
						c.consume();
					} else if (c.peek() == ')') {
						c.consume();
						break;
					} else {
						c.addErrorRange(c.index, c.index + 1, "Expected ',' or ')'");

						if (!c.recoverToNext(',', ')')) {
							return node;
						}

						if (c.peek() == ',') {
							c.consume();
						} else if (c.peek() == ')') {
							c.consume();
							break;
						}
					}
				}

				firstAttribute = false;

				c.skipWhitespace();

				if (annotationClass != null) {
					completeAnnotationAttributes(c, annotationClass, node);
				}

				int keyStart = c.index;
				String key = c.parseIdentifier();

				if (key == null) {
					c.addErrorRange(keyStart, c.index, "Expected attribute name");

					if (!c.recoverToNext(',', ')', '}')) {
						return node;
					}

					continue;
				}

				c.skipWhitespace();

				if (!c.expect('=')) {
					c.addErrorRange(c.index, c.index + 1, "Expected '=' after attribute name");

					if (!c.recoverToNext(',', ')', '}')) {
						return node;
					}

					continue;
				}

				c.skipWhitespace();
				String expectedDesc = annotationClass == null ? null : lookupAnnotationAttributeDescriptor(annotationClass, key);
				int valStart = c.index;
				Object value = c.parseElementValue(expectedDesc, AnnotationNode::new);

				if (value == null) {
					if (!c.recoverToNext(',', ')', '}')) {
						return node;
					}

					continue;
				}

				if (expectedDesc != null) {
					String error = validateValueAgainstDescriptor(value, expectedDesc);

					if (error != null) {
						c.addErrorRange(valStart, c.index, error);
					}
				} else {
					c.addErrorRange(keyStart, keyStart + Math.max(1, key.length()), "Unknown attribute '" + key + "'");
				}

				if (node.values == null) {
					node.values = new ArrayList<>();
				}

				node.values.add(key);
				node.values.add(value);
			}
		} else {
			// single-element form, with implicit 'value'
			int valStart = c.index;
			String expectedDesc = annotationClass == null ? null : lookupAnnotationAttributeDescriptor(annotationClass, "value");
			Object value = c.parseElementValue(expectedDesc, AnnotationNode::new);

			if (value == null) {
				if (!c.recoverToNext(')', ',')) {
					return node;
				}
			} else {
				if (expectedDesc != null) {
					String error = validateValueAgainstDescriptor(value, expectedDesc);

					if (error != null) {
						c.addErrorRange(valStart, c.index, error);
					}
				} else {
					c.addErrorRange(valStart, c.index, "Unknown attribute 'value'");
				}

				if (node.values == null) {
					node.values = new ArrayList<>();
				}

				node.values.add("value");
				node.values.add(value);
			}

			c.skipWhitespace();

			if (c.peek() == ')') {
				c.consume();
			} else {
				c.addErrorRange(c.index, c.index + 1, "Expected ')' after single element");

				if (!c.recoverToNext(')')) {
					return node;
				}

				if (c.peek() == ')') {
					c.consume();
				}
			}
		}

		// check there aren't any missing required attributes
		if (annotationClass != null && annotationClass.methods != null) {
			Set<String> missingRequiredAttributes = new LinkedHashSet<>();

			for (MethodNode method : annotationClass.methods) {
				if ((method.access & Opcodes.ACC_ABSTRACT) != 0 && method.annotationDefault == null) {
					String deobfName = plugin.project.deobfuscate(MethodEntryView.create(annotationClass.name, method.name, method.desc)).getName();
					missingRequiredAttributes.add(deobfName);
				}
			}

			if (node.values != null) {
				for (int i = 0; i < node.values.size(); i += 2) {
					missingRequiredAttributes.remove((String) node.values.get(i));
				}
			}

			if (!missingRequiredAttributes.isEmpty()) {
				c.addErrorRange(nameStart, nameEnd, "Missing required attributes " + String.join(", ", missingRequiredAttributes));
			}
		}

		return node;
	}

	private void completeAnnotationAttributes(Cursor c, ClassNode annotationClass, AnnotationNode node) {
		c.addCompletions(() -> {
			if (annotationClass.methods == null) {
				return List.of();
			}

			Set<String> existingAttributes = new HashSet<>();

			if (node.values != null) {
				for (int i = 0; i < node.values.size(); i += 2) {
					existingAttributes.add((String) node.values.get(i));
				}
			}

			List<CompletionOption> completions = new ArrayList<>();

			for (MethodNode method : annotationClass.methods) {
				String deobfName = plugin.project.deobfuscate(MethodEntryView.create(annotationClass.name, method.name, method.desc)).getName();

				if ((method.access & Opcodes.ACC_ABSTRACT) != 0 && !existingAttributes.contains(deobfName)) {
					String attributeType = deobfuscateType(Type.getReturnType(method.desc)).getClassName();
					String simpleName = attributeType.substring(attributeType.lastIndexOf('.') + 1);
					completions.add(new CompletionOption(deobfName, deobfName + " = ", simpleName.replace('$', '.')));
				}
			}

			return completions;
		});
	}

	public List<ParseError> getErrors() {
		return errors;
	}

	public List<CompletionOption> getCompletions() {
		return completions;
	}

	public record ParseError(int startInclusive, int endExclusive, String message) {
	}

	private class Cursor {
		final String src;
		int index = 0;

		Cursor(String src) {
			this.src = src;
		}

		void addErrorRange(int s, int e, String m) {
			errors.add(new ParseError(s, Math.max(s, e), m));
		}

		void addCompletions(Supplier<List<CompletionOption>> completions) {
			int startIdentifier = index;
			parseIdentifier();
			int endIdentifier = index;
			index = startIdentifier;

			if (caret >= startIdentifier && caret <= endIdentifier) {
				AnnotationParser.this.completions.addAll(completions.get());
			}
		}

		char peek() {
			return index >= src.length() ? (char) -1 : src.charAt(index);
		}

		boolean eof() {
			return index >= src.length();
		}

		char consume() {
			return index >= src.length() ? (char) -1 : src.charAt(index++);
		}

		void skipWhitespace() {
			while (!eof() && Character.isWhitespace(src.charAt(index))) index++;
		}

		boolean expect(char c) {
			if (peek() == c) {
				consume();
				return true;
			}

			return false;
		}

		@Nullable
		String parseIdentifier() {
			int s = index;

			if (s >= src.length()) {
				return null;
			}

			char c = src.charAt(s);

			if (!Character.isJavaIdentifierStart(c)) {
				return null;
			}

			do {
				s++;
			} while (s < src.length() && Character.isJavaIdentifierPart(src.charAt(s)));

			String id = src.substring(index, s);
			index = s;
			return id;
		}

		@Nullable
		String parseQualifiedName() {
			String id = parseIdentifier();

			if (id == null) {
				return null;
			}

			StringBuilder sb = new StringBuilder(id);

			while (true) {
				int save = index;
				skipWhitespace();

				if (peek() != '.') {
					index = save;
					break;
				}

				consume();
				skipWhitespace();
				String next = parseIdentifier();

				if (next == null) {
					index = save;
					break;
				}

				sb.append('.').append(next);
			}

			return sb.toString();
		}

		boolean recoverToNext(char... tokens) {
			while (!eof()) {
				char c = peek();

				for (char tok : tokens) {
					if (c == tok) {
						return true;
					}
				}

				index++;
			}

			return false;
		}

		@Nullable
		Object parseElementValue(@Nullable String expectedDesc, Function<String, AnnotationNode> nestedAnnotationCreator) {
			skipWhitespace();

			addCompletions(() -> {
				if (expectedDesc != null) {
					if (expectedDesc.equals("Ljava/lang/Class;")) {
						AnnotationsIndex index;

						try {
							index = plugin.index.join();
						} catch (Exception e) {
							return List.of();
						}

						return Stream.concat(
								Stream.of("void", "boolean", "byte", "char", "short", "int", "long", "float", "double")
										.map(type -> new CompletionOption(type, type + ".class", null)),
								index.allClasses().stream().map(type -> {
									type = plugin.project.deobfuscate(ClassEntryView.create(type)).getFullName();
									int simpleNameIndex = Math.max(type.lastIndexOf('/'), type.lastIndexOf('$'));
									String simpleName = type.substring(simpleNameIndex + 1);
									String context = simpleNameIndex == -1 ? null : type.substring(0, simpleNameIndex).replace('/', '.').replace('$', '.');
									return new CompletionOption(simpleName, type.replace('/', '.').replace('$', '.') + ".class", context);
								})
						).toList();
					}

					Type expectedType = Type.getType(expectedDesc);

					if (expectedType.getSort() == Type.OBJECT) {
						String enumInternalName = expectedType.getInternalName();
						ClassNode enumClass = plugin.project.getBytecode(plugin.project.obfuscate(ClassEntryView.create(expectedType.getInternalName())).getFullName());

						if (enumClass != null && (enumClass.access & Opcodes.ACC_ENUM) != 0 && enumClass.fields != null) {
							String enumCanonicalName = enumInternalName.replace('/', '.').replace('$', '.');
							String enumSimpleName = enumInternalName.substring(enumCanonicalName.lastIndexOf('.') + 1);
							List<CompletionOption> completions = new ArrayList<>();

							for (FieldNode field : enumClass.fields) {
								if ((field.access & Opcodes.ACC_ENUM) != 0) {
									String deobfName = plugin.project.deobfuscate(FieldEntryView.create(enumClass.name, field.name, field.desc)).getName();
									completions.add(new CompletionOption(deobfName, enumCanonicalName + "." + deobfName, enumSimpleName));
								}
							}

							return completions;
						}
					}
				}

				return List.of();
			});

			int start = index;
			char p = peek();

			if (p == (char) -1) {
				addErrorRange(index, index, "Unexpected EOF when parsing element value");
				return null;
			}

			if (p == '{') {
				consume();
				skipWhitespace();
				List<Object> list = new ArrayList<>();

				if (peek() == '}') {
					consume();
					return list;
				}

				// component descriptor for array
				String compDesc = expectedDesc != null && expectedDesc.startsWith("[") ? expectedDesc.substring(1) : null;

				while (true) {
					Object v = parseElementValue(compDesc, nestedAnnotationCreator);

					if (v == null) {
						if (!recoverToNext(',', '}')) {
							return null;
						}
					} else {
						list.add(v);
					}

					skipWhitespace();

					if (peek() == ',') {
						consume();
						skipWhitespace();
						continue;
					}

					if (peek() == '}') {
						consume();
						break;
					}

					addErrorRange(index, index + 1, "Expected ',' or '}' in array initializer");

					if (!recoverToNext(',', '}')) {
						return null;
					}

					if (peek() == ',') {
						consume();
						skipWhitespace();
						continue;
					}

					if (peek() == '}') {
						consume();
						break;
					}
				}

				return list;
			}

			if (p == '@') {
				AnnotationNode nested = parseAnnotationInstance(this, nestedAnnotationCreator, ann -> true);

				if (nested == null) {
					return null;
				}

				return nested;
			}

			if (p == '"' || p == '\'') {
				boolean isChar = (p == '\'');
				String s = parseStringLiteral();

				if (s == null) {
					addErrorRange(start, index, "Invalid string/char literal");
					return null;
				}

				if (isChar) {
					if (s.length() != 1) {
						addErrorRange(start, index, "Character literal must be a single character");
						return null;
					}

					return s.charAt(0);
				}

				return s;
			}

			// try qualified name (for enum or class literal)
			String q = parseQualifiedName();

			if (q != null) {
				skipWhitespace();

				// array class literal
				if (peek() == '[') {
					int dims = 0;

					while (peek() == '[') {
						consume();
						skipWhitespace();

						if (peek() == ']') {
							consume();
							dims++;
							skipWhitespace();
						} else {
							addErrorRange(index, index + 1, "Expected ']'");
							recoverToNext(',', ')', '}');
							return null;
						}
					}

					skipWhitespace();

					if (peek() == '.') {
						consume();
						skipWhitespace();
						String id = parseIdentifier();

						if ("class".equals(id)) {
							// build array descriptor
							Type t = resolveClassLiteralFromCanonical(q);

							if (t == null) {
								addErrorRange(start, index, "Class literal base type not found: " + q);
								return null;
							}

							return Type.getType("[".repeat(dims) + t.getDescriptor());
						}
					}

					addErrorRange(index, index + 1, "Expected '.class'");
					recoverToNext(',', ')', '}');
					return null;
				}

				// non-array class literal
				if (q.endsWith(".class")) {
					String canonical = q.substring(0, q.length() - ".class".length());
					Type t = resolveClassLiteralFromCanonical(canonical);

					if (t == null) {
						addErrorRange(start, index, "Class literal type not found: " + q);
						return null;
					}

					return t;
				}

				// enum constant: if q contains a dot -> last segment is field, rest is enum type
				int lastDot = q.lastIndexOf('.');

				if (lastDot > 0) {
					String typeCanonical = q.substring(0, lastDot);
					String constName = q.substring(lastDot + 1);
					String intern = resolveToInternalName(typeCanonical);

					if (intern == null) {
						addErrorRange(start, index, "Class not found: " + typeCanonical);
						return null;
					}

					ClassNode cn = plugin.project.getBytecode(plugin.project.obfuscate(ClassEntryView.create(intern)).getFullName());

					if (cn == null || !enumHasConstant(cn, constName)) {
						addErrorRange(start, index, "Enum constant not found: " + typeCanonical);
						return null;
					}

					return new String[] { "L" + intern + ";", constName };
				}

				// boolean literals
				if ("true".equals(q)) {
					return Boolean.TRUE;
				}

				if ("false".equals(q)) {
					return Boolean.FALSE;
				}

				// otherwise it's an error (bare name not allowed as annotation value)
				addErrorRange(start, index, "Unrecognized value: " + q);
				return null;
			}

			// numeric literal
			String num = parseNumberLiteral();

			if (num != null) {
				Object n = interpretNumberLiteralWithExpect(num, expectedDesc);

				if (n == null) {
					addErrorRange(start, index, "Invalid numeric literal: " + num);
					return null;
				}

				return n;
			}

			addErrorRange(index, index + 1, "Unrecognized value");
			return null;
		}

		String parseStringLiteral() {
			if (peek() != '"' && peek() != '\'') {
				return null;
			}

			char q = consume();
			StringBuilder sb = new StringBuilder();
			boolean esc = false;

			boolean valid = true;

			while (!eof()) {
				char c = consume();

				if (esc) {
					switch (c) {
					case 'n' -> sb.append('\n');
					case 'r' -> sb.append('\r');
					case 't' -> sb.append('\t');
					case '\\' -> sb.append('\\');
					case '\'' -> sb.append('\'');
					case '"' -> sb.append('"');
					case 'b' -> sb.append('\b');
					case 'f' -> sb.append('\f');
					case 'u' -> {
						if (index + 4 <= src.length()) {
							String hex = src.substring(index, index + 4);

							try {
								int code = Integer.parseInt(hex, 16);
								sb.append((char) code);
								index += 4;
							} catch (NumberFormatException ex) {
								addErrorRange(index - 2, index + 4, "Invalid Unicode escape");
								valid = false;
							}
						} else {
							addErrorRange(index - 2, src.length(), "Invalid Unicode escape");
							valid = false;
						}
					}
					default -> {
						int endIndex = index + 1;

						// Java allows octal literals between 0 and 377, so that's max 3 digits if the first digit is 0-3, and max 2 digits otherwise
						if (c >= '0' && c <= '3') {
							while (endIndex < src.length() && endIndex < index + 3 && src.charAt(endIndex) >= '0' && src.charAt(endIndex) <= '7') {
								endIndex++;
							}

							sb.append((char) Integer.parseInt(src.substring(index, endIndex), 8));
						} else if (c >= '4' && c <= '7') {
							while (endIndex < src.length() && endIndex < index + 2 && src.charAt(endIndex) >= '0' && src.charAt(endIndex) <= '7') {
								endIndex++;
							}

							sb.append((char) Integer.parseInt(src.substring(index, endIndex), 8));
						} else {
							addErrorRange(index - 2, index, "Invalid escape sequence");
							valid = false;
						}
					}
					}

					esc = false;
				} else {
					if (c == '\\') {
						esc = true;
					} else if (c == q) {
						return valid ? sb.toString() : null;
					} else {
						sb.append(c);
					}
				}
			}

			addErrorRange(index, index, "Unterminated string literal");
			return null;
		}

		String parseNumberLiteral() {
			int s = index;

			if (s >= src.length()) {
				return null;
			}

			char c = src.charAt(s);

			if (c == '+' || c == '-') {
				s++;
				c = src.charAt(s);
			}

			if (!Character.isDigit(c) && c != '.') {
				return null;
			}

			boolean seenDot = false, seenExp = false;
			s++;

			while (s < src.length()) {
				c = src.charAt(s);

				if (Character.isDigit(c)) {
					s++;
					continue;
				}

				if (c == '.' && !seenDot) {
					seenDot = true;
					s++;
					continue;
				}

				if ((c == 'e' || c == 'E') && !seenExp) {
					seenExp = true;
					s++;
					if (s < src.length() && (src.charAt(s) == '+' || src.charAt(s) == '-')) s++;
					continue;
				}

				if (c == 'f' || c == 'F' || c == 'd' || c == 'D' || c == 'l' || c == 'L') {
					s++;
					break;
				}

				break;
			}

			String tok = src.substring(index, s);
			index = s;
			return tok;
		}
	}

	private String validateValueAgainstDescriptor(Object value, @Nullable String expectedDesc) {
		return switch (expectedDesc) {
		case "Z" -> value instanceof Boolean ? null : "Expected boolean";
		case "B" -> value instanceof Byte ? null : "Expected byte";
		case "C" -> value instanceof Character ? null : "Expected char";
		case "S" -> value instanceof Short ? null : "Expected short";
		case "I" -> value instanceof Integer ? null : "Expected int";
		case "J" -> value instanceof Long ? null : "Expected long";
		case "F" -> value instanceof Float ? null : "Expected float";
		case "D" -> value instanceof Double ? null : "Expected double";
		case "Ljava/lang/String;" -> value instanceof String ? null : "Expected String";
		case "Ljava/lang/Class;" -> value instanceof Type ? null : "Expected Class";
		case null -> null; // no expected desc, error already reported so don't make another error
		default -> {
			if (expectedDesc.startsWith("[")) {
				if (!(value instanceof List<?> list)) {
					yield "Expected array for type " + expectedDesc;
				}

				String comp = expectedDesc.substring(1);

				for (Object el : list) {
					String err = validateValueAgainstDescriptor(el, comp);

					if (err != null) {
						yield err;
					}
				}

				yield null;
			}

			if (!expectedDesc.startsWith("L") || !expectedDesc.endsWith(";")) {
				// returns void or something?!
				yield null;
			}

			String internal = expectedDesc.substring(1, expectedDesc.length() - 1);
			String obfInternal = plugin.project.obfuscate(ClassEntryView.create(internal)).getFullName();
			ClassNode cn = plugin.project.getBytecode(obfInternal);

			if (cn == null) {
				// class not found?!
				yield null;
			}

			if ((cn.access & Opcodes.ACC_ENUM) != 0) {
				yield value instanceof String[] enumValue && enumValue[0].equals(expectedDesc)
						? null
						: "Expected enum constant of " + internal.replace('/', '.').replace('$', '.');
			}

			if ((cn.access & Opcodes.ACC_ANNOTATION) != 0) {
				yield value instanceof AnnotationNode ann && ann.desc.equals(expectedDesc)
						? null
						: "Expected annotation of type " + internal.replace('/', '.').replace('$', '.');
			}

			// this type is invalid in an annotation?!
			yield null;
		}
		};
	}

	private String resolveToInternalName(String canonical) {
		String[] parts = canonical.split("\\.");

		for (int packageNamePartCount = parts.length - 1; packageNamePartCount >= 0; packageNamePartCount--) {
			String[] packageNameParts = Arrays.copyOfRange(parts, 0, packageNamePartCount);
			String[] classNameParts = Arrays.copyOfRange(parts, packageNamePartCount, parts.length);
			String possibleInternalName = String.join("/", packageNameParts) + "/" + String.join("$", classNameParts);
			String possibleObfName = plugin.project.obfuscate(ClassEntryView.create(possibleInternalName)).getFullName();

			if (plugin.project.getBytecode(possibleObfName) != null) {
				return possibleInternalName;
			}
		}

		return null;
	}

	private boolean enumHasConstant(ClassNode enumClass, String constName) {
		if (enumClass.fields == null) {
			return false;
		}

		for (FieldNode f : enumClass.fields) {
			String deobfName = plugin.project.deobfuscate(FieldEntryView.create(enumClass.name, f.name, f.desc)).getName();

			if ((f.access & Opcodes.ACC_ENUM) != 0 && deobfName.equals(constName)) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	private Type resolveClassLiteralFromCanonical(String canonical) {
		return switch (canonical) {
		case "byte" -> Type.getType("B");
		case "short" -> Type.getType("S");
		case "int" -> Type.getType("I");
		case "long" -> Type.getType("J");
		case "float" -> Type.getType("F");
		case "double" -> Type.getType("D");
		case "char" -> Type.getType("C");
		case "boolean" -> Type.getType("Z");
		case "void" -> Type.getType("V");
		default -> {
			String internal = resolveToInternalName(canonical);
			yield internal == null ? null : Type.getObjectType(internal);
		}
		};
	}

	private String lookupAnnotationAttributeDescriptor(ClassNode annotationClass, String attrName) {
		if (annotationClass.methods == null) {
			return null;
		}

		for (MethodNode m : annotationClass.methods) {
			String deobfName = plugin.project.deobfuscate(MethodEntryView.create(annotationClass.name, m.name, m.desc)).getName();

			if (deobfName.equals(attrName) && (m.access & Opcodes.ACC_ABSTRACT) != 0) {
				return deobfuscateType(Type.getReturnType(m.desc)).getDescriptor();
			}
		}

		return null;
	}

	private Type deobfuscateType(Type type) {
		return switch (type.getSort()) {
		case Type.ARRAY ->
				Type.getType("[".repeat(type.getDimensions()) + deobfuscateType(type.getElementType()).getDescriptor());
		case Type.OBJECT -> Type.getObjectType(plugin.project.deobfuscate(ClassEntryView.create(type.getInternalName())).getFullName());
		default -> type;
		};
	}

	private Object interpretNumberLiteralWithExpect(String lit, @Nullable String expectedDesc) {
		if (lit.endsWith("l") || lit.endsWith("L")) {
			try {
				return Long.valueOf(lit.substring(0, lit.length() - 1));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		if (lit.endsWith("f") || lit.endsWith("F")) {
			try {
				return Float.valueOf(lit.substring(0, lit.length() - 1));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		if (lit.endsWith("d") || lit.endsWith("D")) {
			try {
				return Double.valueOf(lit.substring(0, lit.length() - 1));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		if (expectedDesc != null) {
			switch (expectedDesc) {
			case "B" -> {
				try {
					return Byte.valueOf(lit);
				} catch (NumberFormatException e) {
					try {
						int v = Integer.parseInt(lit);
						return (byte) v;
					} catch (NumberFormatException ex) {
						return null;
					}
				}
			}
			case "S" -> {
				try {
					return Short.valueOf(lit);
				} catch (NumberFormatException e) {
					try {
						int v = Integer.parseInt(lit);
						return (short) v;
					} catch (NumberFormatException ex) {
						return null;
					}
				}
			}
			case "I" -> {
				try {
					return Integer.valueOf(lit);
				} catch (NumberFormatException e) {
					return null;
				}
			}
			case "J" -> {
				try {
					return Long.valueOf(lit);
				} catch (NumberFormatException e) {
					return null;
				}
			}
			case "F" -> {
				try {
					return Float.valueOf(lit);
				} catch (NumberFormatException e) {
					return null;
				}
			}
			case "D" -> {
				try {
					return Double.valueOf(lit);
				} catch (NumberFormatException e) {
					return null;
				}
			}
			}
		}

		if (lit.indexOf('.') >= 0 || lit.indexOf('e') >= 0 || lit.indexOf('E') >= 0) {
			try {
				return Double.valueOf(lit);
			} catch (Exception e) {
				return null;
			}
		}

		try {
			return Integer.valueOf(lit);
		} catch (Exception e) {
			try {
				return Long.valueOf(lit);
			} catch (Exception ex) {
				return null;
			}
		}
	}
}
