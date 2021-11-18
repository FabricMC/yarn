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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.Taglet;

public final class RecordWarningTaglet implements Taglet {

	private final String warningText;

	public RecordWarningTaglet() {
		try (InputStream stream = RecordWarningTaglet.class.getResourceAsStream("/record_warning.txt")) {
			if (stream == null) {
				throw new IllegalStateException("Cannot find record_warning.txt file!");
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				this.warningText = reader.lines().collect(Collectors.joining("\n"));
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Override
	public Set<Location> getAllowedLocations() {
		return Set.of(Location.TYPE);
	}

	@Override
	public boolean isInlineTag() {
		return false;
	}

	@Override
	public String getName() {
		return "fabricRecordWarning";
	}

	@Override
	public String toString(List<? extends DocTree> tags, Element element) {
		return this.warningText;
	}
}
