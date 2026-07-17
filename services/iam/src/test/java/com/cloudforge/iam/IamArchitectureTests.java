/*
 * Copyright 2026-present Shawn Deng and CloudForge contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudforge.iam;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

@AnalyzeClasses(packages = "com.cloudforge.iam", importOptions = ImportOption.DoNotIncludeTests.class)
final class IamArchitectureTests {

	private IamArchitectureTests() {
	}

	@ArchTest
	static final ArchRule DOMAIN_IS_INDEPENDENT_OF_FRAMEWORKS = ArchRuleDefinition.noClasses()
		.that()
		.resideInAnyPackage("..identity..", "..tenancy..", "..authorization..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..", "com.rabbitmq..",
				"org.springframework.amqp..", "org.springframework.data.redis..")
		.because("the IAM domain model must remain independent of delivery and persistence frameworks")
		.allowEmptyShould(true);

	@ArchTest
	static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_PROTOCOL = ArchRuleDefinition.noClasses()
		.that()
		.resideInAnyPackage("..identity..", "..tenancy..", "..authorization..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("..protocol..")
		.because("OAuth and OIDC are adapters around the IAM domain")
		.allowEmptyShould(true);

	@ArchTest
	static final ArchRule IAM_PACKAGES_ARE_FREE_OF_CYCLES = SlicesRuleDefinition.slices()
		.matching("com.cloudforge.iam.(*)..")
		.should()
		.beFreeOfCycles()
		.because("bounded-context packages need an acyclic dependency direction")
		.allowEmptyShould(true);

}
