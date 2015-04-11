/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2009 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

package org.sonar.plugins.taglist.objectivec;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.sonar.api.CoreProperties;
import org.sonar.api.batch.Decorator;
import org.sonar.api.batch.DecoratorBarriers;
import org.sonar.api.batch.DecoratorContext;
import org.sonar.api.batch.DependedUpon;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.measures.CountDistributionBuilder;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.MetricFinder;
import org.sonar.api.measures.PersistenceMode;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleFinder;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.rules.RuleQuery;
import org.sonar.api.rules.Violation;
import org.sonar.api.utils.SonarException;

@DependsUpon(DecoratorBarriers.END_OF_VIOLATIONS_GENERATION)
public class ViolationsDecorator implements Decorator {

	private static final String OBJC_LANGUAGE_KEY = "objc";
	private static final String OCLINT_REPOSITORY_KEY = "OCLint";

	private static final String TODO_RULE_KEY = "todo comment";
	private static final String FIXME_RULE_KEY = "fixme comment";
	private static final String XXX_RULE_KEY = "xxx comment";
	private static final String NOSONAR_RULE_KEY = "nosonar comment";

	private RulesProfile rulesProfile;
	private RuleFinder ruleFinder;
	private TaglistMetrics taglistMetrics;

	public ViolationsDecorator(RulesProfile rulesProfile, RuleFinder ruleFinder, MetricFinder metricFinder) {
		this.rulesProfile = rulesProfile;
		this.ruleFinder = ruleFinder;
		this.taglistMetrics = new TaglistMetrics(metricFinder);
	}

	@DependedUpon
	public List<Metric> dependedUpon() {
		return Arrays.asList(
				taglistMetrics.getTags(),
				taglistMetrics.getOptionalTags(),
				taglistMetrics.getMandatoryTags(),
				taglistMetrics.getNosonarTags(),
				taglistMetrics.getTagsDistribution()
			);
	}

	@Override
	public boolean shouldExecuteOnProject(Project project) {
		return OBJC_LANGUAGE_KEY.equals(project.getLanguageKey());
	}

	@Override
	public void decorate(@SuppressWarnings("rawtypes") Resource resource, DecoratorContext context) {
		if (Resource.QUALIFIER_FILE.equals(resource.getQualifier())) {
			final Collection<Rule> rules = new HashSet<Rule>();
			rules.addAll(ruleFinder.findAll(RuleQuery.create().withRepositoryKey(OCLINT_REPOSITORY_KEY).withKey(TODO_RULE_KEY)));
			rules.addAll(ruleFinder.findAll(RuleQuery.create().withRepositoryKey(OCLINT_REPOSITORY_KEY).withKey(FIXME_RULE_KEY)));
			rules.addAll(ruleFinder.findAll(RuleQuery.create().withRepositoryKey(OCLINT_REPOSITORY_KEY).withKey(XXX_RULE_KEY)));
			rules.addAll(ruleFinder.findAll(RuleQuery.create().withRepositoryKey(OCLINT_REPOSITORY_KEY).withKey(NOSONAR_RULE_KEY)));
			saveFileMeasures(context, rules);
		}
	}

	protected void saveFileMeasures(final DecoratorContext context, final Collection<Rule> rules) {
		final CountDistributionBuilder distrib = new CountDistributionBuilder(taglistMetrics.getTagsDistribution());
		int mandatory = 0;
		int optional = 0;
		int noSonarTags = 0;
		for (final Rule rule : rules) {
			final ActiveRule activeRule = rulesProfile.getActiveRule(rule);
			if (activeRule != null) {
				for (final Violation violation : context.getViolations()) {
					if (violation.getRule().equals(rule)) {
						if (isMandatory(activeRule.getSeverity())) {
							mandatory++;
						} else {
							optional++;
						}
						if (NOSONAR_RULE_KEY.equals(rule.getKey())) {
							noSonarTags++;
						}
						distrib.add(getTagName(rule));
					}
				}
			}
		}
		saveMeasure(context, taglistMetrics.getTags(), mandatory + optional);
		saveMeasure(context, taglistMetrics.getMandatoryTags(), mandatory);
		saveMeasure(context, taglistMetrics.getOptionalTags(), optional);
		saveMeasure(context, taglistMetrics.getNosonarTags(), noSonarTags);
		if (!distrib.isEmpty()) {
			context.saveMeasure(distrib.build().setPersistenceMode(PersistenceMode.MEMORY));
		}
	}

	protected static boolean isMandatory(final RulePriority priority) {
		return priority.equals(RulePriority.BLOCKER) || priority.equals(RulePriority.CRITICAL);
	}

	private String getTagName(final Rule rule) {
		final String key = rule.getKey();
		if (TODO_RULE_KEY.equals(key)) {
			return "TODO";
		} else if (FIXME_RULE_KEY.equals(key)) {
			return "FIXME";
		} else if (XXX_RULE_KEY.equals(key)) {
			return "XXX";
		} else if (NOSONAR_RULE_KEY.equals(key)) {
			return "NOSONAR";
		}
		throw new SonarException("Taglist plugin doesn't work with rule: " + rule);
	}

	private void saveMeasure(final DecoratorContext context, final Metric metric, final int value) {
		if (value > 0) {
			context.saveMeasure(metric, (double) value);
		}
	}

	@Override
	public String toString() {
		return "Objective-C Taglist Decorator";
	}

}