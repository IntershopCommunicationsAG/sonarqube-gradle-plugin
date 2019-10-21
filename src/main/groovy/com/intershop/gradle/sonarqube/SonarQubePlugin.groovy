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
package com.intershop.gradle.sonarqube

import com.intershop.gradle.sonarqube.cartridge.CartridgeFolders
import com.intershop.gradle.sonarqube.extension.SonarQubeExtension
import com.intershop.gradle.sonarqube.tasks.SonarQubeRunner
import groovy.util.logging.Slf4j
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

import static org.gradle.util.CollectionUtils.join
import static org.gradle.util.CollectionUtils.nonEmptyOrNull
/**
 * This is the plugin implementation of a SonarQubeRunner for Intershop projects.
 * An Intershop component - Cartridge - is from the SonarQube perspective a multi
 * project.
 */
@Slf4j
class SonarQubePlugin implements Plugin<Project> {

    // Intershop artifacts
    public final static String INTERSHOP_PAGELETS = 'pagelets'
    public final static String INTERSHOP_TEMPLATES = 'templates'
    public final static String INTERSHOP_PIPELINES = 'pipelines'
    public final static String INTERSHOP_JAVASCRIPT = 'js'
    public final static String INTERSHOP_CSSFILES = 'css'
    public final static String INTERSHOP_QUERIES = 'queries'
    public final static String INTERSHOP_WEBFRORMS = 'webforms'
    public final static String INTERSHOP_PIPELETS = 'pipelets'

    private SonarQubeExtension extension

    /**
     * Applies the SonarQube plugin to the project.
     *
     * @param project
     */
    void apply (Project project) {

        // Extension is created for all projects
        project.logger.info('Create extension {} for {}', SonarQubeExtension.SONARQUBE_EXTENSION_NAME, project.name)
        extension = project.extensions.findByType(SonarQubeExtension) ?: project.extensions.create(SonarQubeExtension.SONARQUBE_EXTENSION_NAME, SonarQubeExtension, project)

        if(project.allprojects.size() == 1 && project.allprojects[0].name == project.name) {
            addConfiguration(project, extension)
            SonarQubeRunner runner = createTask(project)
            extension.setForkOptions(runner.getForkOptions())
        } else {
            project.logger.debug('This is only a subproject. Intershop Sonar works only with components.')
        }
    }

    /**
     * Creates the task for the plugin.
     *
     * @param project
     * @return SonarQubeRunner task
     */
    private SonarQubeRunner createTask(final Project project) {
        SonarQubeRunner task  = project.tasks.findByName(SonarQubeExtension.SONARQUBE_TASK_NAME)
        if(! task) {
            task = project.getTasks().create(SonarQubeExtension.SONARQUBE_TASK_NAME, SonarQubeRunner.class).configure {
                description = "Analyzes ${project} and its subprojects with Sonar Runner."
                group = 'Verification'
            }
        }
        task.onlyIf { ! extension.skipProject && ! project.hasProperty('skipSonar')}

        task.conventionMapping.sonarProperties = {
            return computeSonarProperties(project, extension.getSonarProperties().properties)
        }

        task.dependsOn {
            return project.tasks.findByName(JavaPlugin.TEST_TASK_NAME)
        }

        return task
    }

    /**
     * Calculates the properties for the sonar task.
     *
     * @param project
     * @param properties
     */
    private Map<String, Object> computeSonarProperties(Project project, Map<String, Object> properties) {
        if (extension.isSkipProject()) {
            return;
        }

        Map<String, Object> rawProperties = [:]
        List<String> modules = []


        if(properties.get('sonar.modules')) {
            properties.get('sonar.modules').split(',').each {
                modules.add(it.toString().trim())
            }
        }

        configureProject(project, rawProperties)
        configureJava(project, extension.getModules(), rawProperties, modules)

        configureIntershopArtifacts(project, extension.getModules(), rawProperties, modules)
        configureSonarProperties(rawProperties)
        addSystemProperties(rawProperties)

        rawProperties.put('sonar.modules', modules.unique())

        properties.each { String key, Object value ->
            if(key != 'sonar.modules') {
                rawProperties.put(key, value)
            }
        }

        return convertProperties(rawProperties)
    }

    /**
     * Configures project settings
     *
     * @param project
     * @param properties
     */
    private void configureProject(final Project project, Map<String, Object> properties)  {
        // this is a component build ...
        properties.put('sonar.projectName', project.name)
        properties.put('sonar.projectKey' , "${project.group}:${project.name}")
        properties.put('sonar.projectDescription', project.description ?: '')
        properties.put('sonar.projectBaseDir', project.projectDir)

        if(project.hasProperty('branchVersion') && project.ext.branchVersion) {
            properties.put('sonar.branch', project.ext.branchVersion)
            properties.put('sonar.projectVersion', project.ext.branchVersion)
        } else {
            String projectVersion = project.getVersion().toString()
            String pVersion = projectVersion

            if(projectVersion.contains('-SNAPSHOT')) {
                pVersion = projectVersion - '-SNAPSHOT'
            }
            if(projectVersion.contains('-LOCAL')) {
                pVersion = projectVersion - '-LOCAL'
            }
            properties.put('sonar.projectVersion', pVersion)
        }

        properties.put('sonar.working.directory', new File(project.buildDir, 'sonarqube'))
        properties.put('sonar.environment.information.key', 'Gradle')
        properties.put('sonar.environment.information.version', project.gradle.gradleVersion)
    }

    /**
     * Read system properties
     *
     * @param properties
     */
    private void addSystemProperties(Map<String, Object> properties) {
        System.getProperties().each {key, value ->
            if(key.startsWith('sonar.')) {
                properties.put(key, value)
            }
        }
    }

    /**
     * Configures special sonar properties
     *
     * @param properties
     */
    private void configureSonarProperties(Map<String, Object> properties) {
        if(extension.runOnCI) {
            properties.put('sonar.importSources', 'true')
        } else {
            properties.put('sonar.importSources', 'false')
            properties.put('sonar.analysis.mode', 'preview')
            properties.put('sonar.issuesReport.html.enable', 'true')
        }

        if(extension.verbose) {
            properties.put('sonar.verbose', 'true')
        }
    }

    /**
     * Configures Java project settings
     *
     * @param project
     * @param confModules
     * @param properties
     * @param modules
     */
    private void configureJava(final Project project, List<String> confModules, Map<String, Object> properties, List<String> modules)  {
        project.plugins.withType(JavaBasePlugin) {
            JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class)

            // main source set
            SourceSet main = javaPluginConvention.getSourceSets().findByName('main')
            List<File> srcDirectories = main ? nonEmptyOrNull(main.allSource.srcDirs.findAll { it.exists() }.toList()) : []

            // test source set
            SourceSet test = javaPluginConvention.getSourceSets().findByName('test')
            List<File> testDirectories = test ? nonEmptyOrNull(test.allSource.srcDirs.findAll {it.exists()}.toList()) : []

            if(srcDirectories && confModules.contains('java')) {
                modules.add('java')

                properties.put('java.sonar.projectBaseDir', project.projectDir)

                properties.put('sonar.java.source', javaPluginConvention.sourceCompatibility)
                properties.put('sonar.java.target', javaPluginConvention.targetCompatibility)

                properties.put('java.sonar.projectName', 'Java')
                properties.put('java.sonar.language', 'java')
                properties.put('java.sonar.sources', srcDirectories)

                properties.put('sonar.java.binaries', nonEmptyOrNull(main.getRuntimeClasspath().findAll {it.isDirectory()}.toList()))

                List<File> mainLibs = main.getRuntimeClasspath().findAll {it.exists() && (! it.isDirectory()) && (it.getName().endsWith('.jar') || it.getName().endsWith('.zip'))}.toList()

                File runtimeJar = getRuntimeJar()
                if (runtimeJar != null) {
                    mainLibs.add(runtimeJar)
                }
                File fxRuntimeJar = getFxRuntimeJar()
                if (fxRuntimeJar != null) {
                    mainLibs.add(fxRuntimeJar)
                }

                properties.put('sonar.java.libraries', nonEmptyOrNull(mainLibs))

                List<File> newTestDirs = []

                if(testDirectories) {
                    List<File> commons = testDirectories.intersect(srcDirectories)
                    newTestDirs.addAll(testDirectories)
                    newTestDirs.removeAll(commons)
                }

                if(newTestDirs) {
                    //remove source directories from testDirectories
                    properties.put('java.sonar.tests', newTestDirs)

                    final Test testTask = (Test) project.tasks.findByName(JavaPlugin.TEST_TASK_NAME)

                    //test task must be available
                    //test dir and src dir must be configured
                    //test and src dir must be different
                    if (testTask && testDirectories && srcDirectories) {

                        properties.put('sonar.java.test.binaries', nonEmptyOrNull(test.getRuntimeClasspath().findAll {it.isDirectory()}.toList()))
                        List<File> testLibs = test.getRuntimeClasspath().findAll {it.exists() && (! it.isDirectory()) && (it.getName().endsWith('.jar') || it.getName().endsWith('.zip'))}.toList()
                        properties.put('sonar.java.test.libraries', nonEmptyOrNull(testLibs))

                        File testResultsDir = testTask.reports.junitXml.destination
                        testResultsDir.mkdirs()

                        properties.put('sonar.surefire.reportsPath', testResultsDir.absolutePath)
                        properties.put('sonar.junit.reportsPath', testResultsDir.absolutePath)

                        project.plugins.withType(JacocoPlugin) {
                            JacocoTaskExtension jacocoTaskExtension = testTask.extensions.getByType(JacocoTaskExtension.class)
                            File destinationFile = jacocoTaskExtension.getDestinationFile()
                            if (destinationFile.exists()) {
                                properties.put('sonar.java.coveragePlugin', 'jacoco')
                                properties.put('java.sonar.jacoco.reportPath', destinationFile)
                            }
                        }
                    }
                }
            }

            CartridgeFolders cfolders = new CartridgeFolders(project)

            if(srcDirectories && confModules.contains(INTERSHOP_PIPELETS) && !cfolders.pipeletsFileTree.isEmpty()) {
                modules.add(INTERSHOP_PIPELETS)

                properties.put('pipelets.sonar.projectName', 'Pipelets')
                properties.put('pipelets.sonar.sources', srcDirectories)
                properties.put('pipelets.sonar.binaries', nonEmptyOrNull(main.getRuntimeClasspath().findAll {it.isDirectory()}.toList()))
                properties.put('pipelets.sonar.projectBaseDir', project.projectDir)
                properties.put('pipelets.sonar.language', 'pplet')
            }
        }
    }

    private static File getRuntimeJar() {
        try {
            final File javaBase = new File(System.getProperty("java.home")).getCanonicalFile()
            File runtimeJar = new File(javaBase, "lib/rt.jar")
            if (runtimeJar.exists()) {
                return runtimeJar
            }
            runtimeJar = new File(javaBase, "jre/lib/rt.jar")
            return runtimeJar.exists() ? runtimeJar : null;
        } catch (IOException e) {
            throw new IllegalStateException(e)
        }
    }

    private static File getFxRuntimeJar() {
        try {
            final File javaBase = new File(System.getProperty("java.home")).getCanonicalFile()
            File runtimeJar = new File(javaBase, "lib/ext/jfxrt.jar")
            if (runtimeJar.exists()) {
                return runtimeJar
            }
            runtimeJar = new File(javaBase, "jre/lib/ext/jfxrt.jar")
            return runtimeJar.exists() ? runtimeJar : null
        } catch (IOException e) {
            throw new IllegalStateException(e)
        }

    }

    /**
     * Configures othere Intershop cartridges artifacts
     *
     * @param project
     * @param confModules
     * @param properties
     * @param modules
     */
    private void configureIntershopArtifacts(Project project, List<String> confModules, Map<String, Object> properties, List<String> modules) {
        CartridgeFolders cfolders = new CartridgeFolders(project)

        if (confModules.contains(INTERSHOP_PAGELETS) && !cfolders.pageletsFileTree.isEmpty()) {
            modules.add(INTERSHOP_PAGELETS)

            properties.put('pagelets.sonar.projectName', 'Pagelets')
            properties.put('pagelets.sonar.sources', "${CartridgeFolders.STATICFILES_FOLDER}/${CartridgeFolders.CARTRIDGE_FOLDER}/${CartridgeFolders.PAGELETS_FOLDER}")
            properties.put('pagelets.sonar.projectBaseDir', project.projectDir.absolutePath)
            properties.put('pagelets.sonar.language', 'pglet')
        }

        if (confModules.contains(INTERSHOP_TEMPLATES) && !cfolders.templatesFileTree.isEmpty()) {
            modules.add(INTERSHOP_TEMPLATES)

            properties.put('templates.sonar.projectName', 'Templates')
            properties.put('templates.sonar.sources', "${CartridgeFolders.STATICFILES_FOLDER}/${CartridgeFolders.CARTRIDGE_FOLDER}/${CartridgeFolders.TEMPLATES_FOLDER}")
            properties.put('templates.sonar.projectBaseDir', project.projectDir.absolutePath)
            properties.put('templates.sonar.language', 'isml')
        }

        if (confModules.contains(INTERSHOP_PIPELINES) && !cfolders.pipelinesFileTree.isEmpty()) {
            modules.add(INTERSHOP_PIPELINES)

            properties.put('pipelines.sonar.projectName', 'Pipelines')
            properties.put('pipelines.sonar.sources', "${CartridgeFolders.STATICFILES_FOLDER}/${CartridgeFolders.CARTRIDGE_FOLDER}/${CartridgeFolders.PIPELINES_FOLDER}")
            properties.put('pipelines.sonar.projectBaseDir', project.projectDir.absolutePath)
            properties.put('pipelines.sonar.language', 'pline')
        }

        if (confModules.contains(INTERSHOP_JAVASCRIPT) && !cfolders.JSFileTree.isEmpty()) {
            modules.add(INTERSHOP_JAVASCRIPT)

            properties.put('js.sonar.projectName', 'Javascript')
            properties.put('js.sonar.sources', "${CartridgeFolders.STATICFILES_FOLDER}/${CartridgeFolders.CARTRIDGE_FOLDER}/${CartridgeFolders.STATIC_FOLDER}")
            properties.put('js.sonar.projectBaseDir', project.projectDir.absolutePath)
            properties.put('js.sonar.language', 'js')
        }

        if (confModules.contains(INTERSHOP_CSSFILES) && !cfolders.CSSFileTree.isEmpty()) {
            modules.add(INTERSHOP_CSSFILES)

            properties.put('css.sonar.projectName', 'CSS')
            properties.put('css.sonar.sources', "${CartridgeFolders.STATICFILES_FOLDER}/${CartridgeFolders.CARTRIDGE_FOLDER}/${CartridgeFolders.STATIC_FOLDER}")
            properties.put('css.sonar.projectBaseDir', project.projectDir.absolutePath)
            properties.put('css.sonar.language', 'css')
        }

        if (confModules.contains(INTERSHOP_QUERIES) && !cfolders.queriesFileTree.isEmpty()) {
            modules.add(INTERSHOP_QUERIES)

            properties.put('queries.sonar.projectName', 'Query')
            properties.put('queries.sonar.sources', "${CartridgeFolders.STATICFILES_FOLDER}/${CartridgeFolders.CARTRIDGE_FOLDER}/${CartridgeFolders.QUERIES_FOLDER}")
            properties.put('queries.sonar.projectBaseDir', project.projectDir.absolutePath)
            properties.put('queries.sonar.language', 'query')
        }

        if (confModules.contains(INTERSHOP_WEBFRORMS) && !cfolders.webformsFileTree.isEmpty()) {
            modules.add(INTERSHOP_WEBFRORMS)

            properties.put('webforms.sonar.projectName', 'Webform')
            properties.put('webforms.sonar.sources', "${CartridgeFolders.STATICFILES_FOLDER}/${CartridgeFolders.CARTRIDGE_FOLDER}/${CartridgeFolders.WEBFORMS_FOLDER}")
            properties.put('webforms.sonar.projectBaseDir', project.projectDir.absolutePath)
            properties.put('webforms.sonar.language', 'webfm')
        }
    }

    /**
     * Converts properties values from Object to String
     *
     * @param rawProperties
     * @param properties
     */
    private Map<String, Object> convertProperties(Map<String, Object> rawProperties) {
        Map<String, Object> returnProperties = [:]

        rawProperties.each { key, value ->
            returnProperties.put(key, convertValue(value))
        }

        return returnProperties
    }

    /**
     * Convert value from object to string
     *
     * @param value
     * @return string value
     */
    private String convertValue(Object value) {
        if (value == null) {
            return ''
        }
        if (value instanceof Iterable<?>) {
            Iterable<String> flattened = value.collect {convertValue(it)}
            Iterable<String> filtered = flattened.findAll {it != null}
            String joined = filtered.join(',')
            return joined.isEmpty() ? '' : joined
        } else {
            return value.toString()
        }
    }

    /**
     * Adds configuration for SonarRunner
     *
     * @param project
     * @param extension
     */
    private void addConfiguration(final Project project, final SonarQubeExtension extension) {
        final Configuration configuration = project.getConfigurations().maybeCreate(SonarQubeExtension.SONARQUBE_CONFIGURATION_NAME)
        configuration
                .setVisible(false)
                .setTransitive(false)
                .setDescription("The SonarRunner configuration to use to run analysis")
                .defaultDependencies { dependencies  ->
            DependencyHandler dependencyHandler = project.getDependencies()
            dependencies.add(dependencyHandler.create("org.sonarsource.scanner.cli:sonar-scanner-cli:" + extension.getToolVersion()))
        }
    }
}