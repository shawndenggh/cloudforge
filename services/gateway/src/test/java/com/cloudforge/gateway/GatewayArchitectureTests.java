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
package com.cloudforge.gateway;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

@AnalyzeClasses(packages = "com.cloudforge.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
final class GatewayArchitectureTests {

	private GatewayArchitectureTests() {
	}

	@ArchTest
	static final ArchRule GATEWAY_DOES_NOT_DEPEND_ON_IAM_IMPLEMENTATION = ArchRuleDefinition.noClasses()
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("com.cloudforge.iam..")
		.because("the gateway must stay a thin protocol and routing boundary");

}
