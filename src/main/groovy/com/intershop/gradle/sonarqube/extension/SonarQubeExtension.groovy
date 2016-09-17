/*
 * Copyright 2015 Intershop Communications AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intershop.gradle.sonarqube.extension

import com.intershop.gradle.sonarqube.utils.SonarProperties
import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.process.JavaForkOptions

/**
 * This is the plugin extension for the Intershop SonarQube
 * Plugin. It implements the 'sonarqube' configuration.
 */
@Slf4j
class SonarQubeExtension {

    // run on CI server
    public final static String RUNONCI_ENV = 'RUNONCI'
    public final static String RUNONCI_PRJ = 'runOnCI'

    // default version of Sonar Runner
    public static final String DEFAULT_SONAR_RUNNER_VERSION = '2.7'

    // names for the plugin
    public static final String SONARQUBE_CONFIGURATION_NAME = "sonarqube"
    public static final String SONARQUBE_EXTENSION_NAME = "sonarqube"
    public static final String SONARQUBE_TASK_NAME = "sonarqube"

    final private Project project

    /**
     * Construct the extension with some default values.
     *
     * @param project the base project
     */
    SonarQubeExtension(Project project) {
        this.project = project

        this.sonarProperties = new SonarProperties()

        this.toolVersion = DEFAULT_SONAR_RUNNER_VERSION

        if(! runOnCI) {
            runOnCI = Boolean.parseBoolean(getVariable(RUNONCI_ENV, RUNONCI_PRJ, 'false'))
            if(runOnCI) {
                log.warn('All tasks will be executed on a CI build environment.')
            }
        }
        if(! modules) {
            modules = ['java', 'js', 'css']
        }

        skipProject = false

        verbose = false
    }

    /**
     * <p>Configuration for available modules</p>
     * <p>Available modules: java, js, css</p>
     * <p>Support planned: pipelets, templates, webforms, queries, pipelines</p>
     */
    List<String> modules

    /**
     * <p>Configuration for the execution on the CI server</p>
     *
     * <p>Can be configured/overwritten with environment variable RUNONCI;
     * java environment RUNONCI or project variable runOnCI</p>
     */
    boolean runOnCI

    /**
     * <p>Configuration for the version of the Sonar Runner</p>
     * <p>The default value is 2.4. Please check your server version and the
     * required Sonar Runner version.</p>
     */
    String toolVersion

    /**
     * It is possible to skip the project. The default value is false.
     */
    boolean skipProject

    /**
     * With this configuration the verbose output of the Sonar Runner can be enabled.
     */
    boolean verbose

    /**
     * This configuration holds all Sonar properties.
     * {@code
     *      sonarProperties {
     *          property sonar.property1, value1
     *          property sonar.property2, value2
     *      }
     * }
     * Properties will be preconfigured from the plugin.
     */
    SonarProperties sonarProperties

    void sonarProperties(Closure c) {
        project.configure(sonarProperties, c)
    }

    /**
     * This configures the special options for the used VM.
     */
    JavaForkOptions forkOptions

    void forkOptions(Closure c) {
        project.configure(forkOptions, c)
    }

    /**
     * Calculates the setting for special configuration from the system
     * or java environment or project properties.
     *
     * @param envVar        name of environment variable
     * @param projectVar    name of project variable
     * @param defaultValue  default value
     * @return              the string configuration
     */
    protected String getVariable(String envVar, String projectVar, String defaultValue) {
        if(System.properties[envVar]) {
            log.debug('Specified from system property {}.', envVar)
            return System.properties[envVar].toString().trim()
        } else if(System.getenv(envVar)) {
            log.debug('Specified from system environment property {}.', envVar)
            return System.getenv(envVar).toString().trim()
        } else if(project.hasProperty(projectVar) && project."${projectVar}") {
            log.debug('Specified from project property {}.', projectVar)
            return project."${projectVar}".toString().trim()
        }
        return defaultValue
    }
}