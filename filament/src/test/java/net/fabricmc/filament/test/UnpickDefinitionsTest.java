package net.fabricmc.filament.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class UnpickDefinitionsTest extends ProjectTest {
	@Test
	void remapUnpickDefinitionsIntermediary() throws IOException {
		setupProject(
				"unpickDef",
				"unpick-definitions/screen_handler_slot_ids.unpick",
				"unpick-definitions/set_block_state_flags.unpick"
		);
		copyYarnV2Data("yarn-mappings-v2.tiny");

		BuildResult result = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(projectDirectory)
				.withArguments("remapUnpickDefinitionsIntermediary")
				.build();

		assertThat(result.task(":combineUnpickDefinitions").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
		assertThat(result.task(":remapUnpickDefinitionsIntermediary").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
		assertThat(new File(projectDirectory, "combined_definitions.unpick")).exists().hasContent(getProjectFileText("unpickDef", "expected_named.unpick"));
		assertThat(new File(projectDirectory, "intermediary_definitions.unpick")).exists().hasContent(getProjectFileText("unpickDef", "expected_intermediary.unpick"));
	}
}
