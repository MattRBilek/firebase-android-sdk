// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins.license

import com.google.firebase.gradle.plugins.license.RemoteLicenseFetcher.AnotherMITLicenseFetcher
import com.google.firebase.gradle.plugins.license.RemoteLicenseFetcher.AndroidSdkTermsFetcher
import com.google.firebase.gradle.plugins.license.RemoteLicenseFetcher.AnotherApache2LicenseFetcher
import com.google.firebase.gradle.plugins.license.RemoteLicenseFetcher.Apache2LicenseFetcher
import com.google.firebase.gradle.plugins.license.RemoteLicenseFetcher.BSDLicenseFetcher
import com.google.firebase.gradle.plugins.license.RemoteLicenseFetcher.CreativeCommonsLicenseFetcher
import com.google.firebase.gradle.plugins.license.RemoteLicenseFetcher.GnuClasspathLicenseFetcher
import com.google.firebase.gradle.plugins.license.RemoteLicenseFetcher.MITLicenseFetcher
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Interprets the implementation config for the underlying project and generates license artifacts.
 * Gradle configuration phase consists of the following steps:
 * <ul>
 *     <li> Apply the com.jaredsburrows.license plugin onto the project
 *     <li> Defines a generateLicenses task common to java and android projects
 *     <li> Additional licenses may be configured by projects using the
 *     {@link ThirdPartyLicensesExtension}
 * </ul>
 *
 * Execution phase consists of the following steps
 * <ul>
 *     <li> Remote url licenses are deduped and downloaded.
 *     <li> The licenseReleaseReport and licenseReport tasks defined by the
 *     com.jaredsburrows.license plugin are executed for android and java projects respectively.
 *     <li> The license report (json) generated by the com.jaredsburrows.license is used by the
 *     {@link GenerateLicensesTask} to create the artifacts necessary for our release.
 * </ul>
 */
class LicenseResolverPlugin implements Plugin<Project> {
    List<RemoteLicenseFetcher> remoteLicenseFetchers =
            [new AndroidSdkTermsFetcher(),
             new Apache2LicenseFetcher(),
             new BSDLicenseFetcher(),
             new AnotherApache2LicenseFetcher(),
             new CreativeCommonsLicenseFetcher(), new MITLicenseFetcher(), new AnotherMITLicenseFetcher(), new GnuClasspathLicenseFetcher()]
    final static ANDROID_PLUGINS = ["com.android.application", "com.android.library",
                                    "com.android.test"]

    @Override
    void apply(Project project) {
        ThirdPartyLicensesExtension thirdPartyLicenses =
                project.extensions.create('thirdPartyLicenses', ThirdPartyLicensesExtension,
                        project.getRootDir())

        project.afterEvaluate {
            def conf = project.configurations.create('allExternalDependencies')
            def targetConfiguration = project.plugins.hasPlugin('com.android.library') ? 'releaseRuntimeClasspath' : 'runtimeClasspath'

            project.configurations.all { Configuration c ->
                if (c.name == targetConfiguration) {
                    conf.extendsFrom c
                }
            }

            File downloadsDir = new File("$project.buildDir/generated/downloads")
            File licensesDir = new File("$project.buildDir/generated/third_party_licenses")

            DownloadLicenseTask downloadLicensesTask = project.task('downloadLicenses',
                    type: DownloadLicenseTask) {
                description "Downloads remote licenses"
                outputDir = downloadsDir
                parsers = remoteLicenseFetchers
            }

            if (isAndroidProject(project)) {

                def licensesTask = project.tasks.create("generateLicenses", GenerateLicensesTask, conf).configure {
                    dependsOn downloadLicensesTask
                    additionalLicenses = thirdPartyLicenses.getLibraries()
                    licenseDownloadDir = downloadsDir
                    outputDir = licensesDir
                }

                project.tasks.getByName("bundleReleaseAar") {
                    dependsOn licensesTask
                    from licensesTask.outputDir
                }
            }

        }
    }

    static isAndroidProject(project) {
        ANDROID_PLUGINS.find { plugin -> project.plugins.hasPlugin(plugin) }
    }
}