package net.fabricmc.filament.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JavadocLintTest extends ProjectTest {
	private BuildResult runGradleBuild(boolean shouldSucceed) {
		GradleRunner runner = GradleRunner.create()
				.withPluginClasspath()
				.withProjectDir(projectDirectory)
				.withArguments("javadocLint");

		return shouldSucceed ? runner.build() : runner.buildAndFail();
	}

	@Test
	public void paramInMethod() {
		setupProject("javadocLint", "mappings/ParamInMethod.mapping");
		BuildResult result = runGradleBuild(false);

		assertThat(result.task(":javadocLint").getOutcome()).isEqualTo(TaskOutcome.FAILED);
		assertThat(result.getOutput()).contains("method javadoc contains parameter docs");
	}

	@Test
	public void periodInParam() {
		setupProject("javadocLint", "mappings/ParamPeriod.mapping");
		BuildResult result = runGradleBuild(false);

		assertThat(result.task(":javadocLint").getOutcome()).isEqualTo(TaskOutcome.FAILED);
		assertThat(result.getOutput()).contains("parameter javadoc ends with '.'");
	}

	@Test
	public void uppercaseParam() {
		setupProject("javadocLint", "mappings/UppercaseParam.mapping");
		BuildResult result = runGradleBuild(false);

		assertThat(result.task(":javadocLint").getOutcome()).isEqualTo(TaskOutcome.FAILED);
		assertThat(result.getOutput()).contains("parameter javadoc starts with uppercase word 'The'");
	}

	@Test
	public void multipleErrors() {
		setupProject(
				"javadocLint",
				"mappings/ParamInMethod.mapping",
				"mappings/ParamPeriod.mapping",
				"mappings/UppercaseParam.mapping"
		);
		BuildResult result = runGradleBuild(false);

		assertThat(result.task(":javadocLint").getOutcome()).isEqualTo(TaskOutcome.FAILED);
		assertThat(result.getOutput()).contains("Found 3 javadoc format errors");
	}

	@Test
	public void successful() {
		setupProject("javadocLint", "mappings/Successful.mapping");
		BuildResult result = runGradleBuild(true);

		assertThat(result.task(":javadocLint").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
	}
}
