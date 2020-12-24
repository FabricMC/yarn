/*
 * Copyright (c) 2020 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.mappingpoet.jd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import jdk.javadoc.doclet.Taglet;

@SuppressWarnings("unused")
public final class MappingTaglet implements Taglet {

	private static final String JS_CONTENT;

	public MappingTaglet() {
		// Required by javadoc
	}

	@Override
	public Set<Location> getAllowedLocations() {
		return EnumSet.of(Location.TYPE, Location.CONSTRUCTOR, Location.METHOD, Location.FIELD);
	}

	@Override
	public boolean isInlineTag() {
		return false;
	}

	@Override
	public String getName() {
		return "mapping";
	}

	@Override
	public String toString(List<? extends DocTree> tags, Element element) {
		boolean typeDecl = element instanceof TypeElement; // means it's a class, itf, enum, etc.
		StringBuilder builder = new StringBuilder();
		builder.append("<dt>Mappings:</dt>\n");
		// Java 15 required for style consistency
		builder.append("<dd><div class=\"member-summary\"><table class=\"summary-table\" summary=\"Mapping data\">\n");
		builder.append("<thead>\n");
		builder.append("<th class=\"col-first\">Namespace</th>\n");
		builder.append("<th class=\"").append(typeDecl ? "col-second" : "col-last").append("\">Name</th>\n");
		if (!typeDecl) {
			builder.append("<th class=\"col-last\" scope=\"col\">Mixin selector</th>\n");
		}
		builder.append("</thead>\n");
		builder.append("<tbody>\n");

		boolean altColor = true;
		for (DocTree each : tags) {
			String body = ((UnknownBlockTagTree) each).getContent().stream().map(t -> ((LiteralTree) t).getBody().getBody()).collect(Collectors.joining());
			String[] ans = body.split(":", 3);
			builder.append("<tr class=\"").append(altColor ? "alt-color" : "row-color").append("\">\n");
			builder.append(String.format("<td class=\"col-first\">%s</td>\n", escaped(ans[0])));
			builder.append(String.format("<td class=\"col-second\"><span class=\"copyable\"><code>%s</code></span></td>\n", escaped(ans[1])));
			if (!typeDecl) {
				builder.append(String.format("<td class=\"col-last\" scope=\"row\"><span class=\"copyable\"><code>%s</code></span></td>\n", escaped(ans[2])));
			}
			builder.append("</tr>\n");
			altColor = !altColor;
		}

		builder.append("</tbody>\n");
		builder.append("</table></div></dd>\n");
		if (typeDecl) {
			builder.append("<script>\n");
			builder.append(JS_CONTENT);
			builder.append("</script>\n");
		}
		return builder.toString();
	}

	// I hate <init>
	private static String escaped(String original) {
		StringBuilder builder = new StringBuilder(original.length());
		final int len = original.length();
		for (int i = 0; i < len; i++) {
			char c = original.charAt(i);
			if (c > 127 || c == '"' || c == '\'' || c == '<' || c == '>' || c == '&') {
				builder.append("&#").append((int) c).append(";");
			} else {
				builder.append(c);
			}
		}
		return builder.toString();
	}

	static {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(MappingTaglet.class.getResourceAsStream("/copy_on_click.js"), "copy_on_click.js stream"), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		JS_CONTENT = sb.toString();
	}
}
