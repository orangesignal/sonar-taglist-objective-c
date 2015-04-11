package org.sonar.plugins.taglist.objectivec;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

public class TaglistObjectiveCPluginTest {

	private TaglistObjectiveCPlugin plugin;

	@Before
	public void setUp() {
		plugin = new TaglistObjectiveCPlugin();
	}

	@Test
	public void testPluginDefinition() {
		assertThat(plugin.getExtensions().size(), greaterThan(0));
	}

	/**
	 * see SONAR-1898
	 */
	@Test
	public void testDeprecatedMethods() {
		assertThat(plugin.getKey(), notNullValue());
		assertThat(plugin.getName(), notNullValue());
//		assertThat(plugin.getDescription(), notNullValue());
	}

}
