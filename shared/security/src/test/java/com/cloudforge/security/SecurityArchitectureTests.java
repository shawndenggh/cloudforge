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
package com.cloudforge.security;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

@AnalyzeClasses(packages = "com.cloudforge.security", importOptions = ImportOption.DoNotIncludeTests.class)
final class SecurityArchitectureTests {

	private SecurityArchitectureTests() {
	}

	@ArchTest
	static final ArchRule SECURITY_LIBRARY_HAS_NO_BOOT_OR_SERVICE_IMPLEMENTATION_DEPENDENCIES = ArchRuleDefinition
		.noClasses()
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("com.cloudforge.iam..", "com.cloudforge.gateway..", "com.cloudforge.messaging..",
				"jakarta.persistence..", "org.hibernate..", "org.springframework.boot..")
		.because("the security library must remain independent from Spring Boot and deployable services");

	@ArchTest
	static final ArchRule SECURITY_LIBRARY_HAS_NO_TENANT_OR_OAUTH2_IDENTITY_SEAM = ArchRuleDefinition.noClasses()
		.should()
		.haveSimpleNameContaining("Tenant")
		.orShould()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework.security.oauth2..")
		.because("the shared identity seam carries only the trusted global user ID");

}
