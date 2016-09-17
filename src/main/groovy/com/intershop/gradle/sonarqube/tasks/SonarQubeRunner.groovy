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
package com.intershop.gradle.sonarqube.tasks
import com.google.common.base.Joiner
import com.intershop.gradle.sonarqube.extension.SonarQubeExtension
import groovy.util.logging.Slf4j
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.DefaultJavaForkOptions
import org.gradle.process.internal.JavaExecHandleBuilder
import org.gradle.util.GUtil

import javax.inject.Inject
/**
 * SonarQubeRunner is a fork of the original Gradle task.
 * It start a separate Java process, therefore it extends DefaultTask.
 */
@Slf4j
class SonarQubeRunner  extends DefaultTask {

    private static final String MAIN_CLASS_NAME = 'org.sonarsource.scanner.cli.Main'

    /**
     * Java fork options for the Java task.
     */
    JavaForkOptions forkOptions

    /**
     * These are the Sonar properties.
     */
    @Input
    Map<String, Object> sonarProperties

    /**
     * Task action of the SonarQube runner
     */
    @TaskAction
    public void run() {

            JavaExecHandleBuilder exechandler = prepareExec()
            if (exechandler) {
                exechandler.build().start().waitForFinish().assertNormalExitValue()
            }
    }

    /**
     * Prepares the JavaExecHandlerBuilder for the task.
     *
     * @return JavaExecHandleBuilder
     */
    JavaExecHandleBuilder prepareExec() {
        Map<String, Object> properties = getSonarProperties()

        if (project.file('sonar-project.properties').exists()) {
            log.warn("Found 'sonar-project.properties' in project directory: SonarQube Runner may read this file to override the Gradle 'sonarRunner' configuration.")
        }
        if(log.isInfoEnabled()) {
            log.info('Executing SonarQube Runner with properties:\n[{}]', Joiner.on(', ').withKeyValueSeparator(': ').join(properties))
        }
        if(! properties.containsKey('sonar.host.url')) {
            log.error("It is necessary to specify 'sonar.host.url'")
        } else {

            JavaExecHandleBuilder javaExec = new JavaExecHandleBuilder(getFileResolver());

            getForkOptions().copyTo(javaExec);

            FileCollection sonarRunnerConfiguration = getProject().getConfigurations().getAt(SonarQubeExtension.SONARQUBE_CONFIGURATION_NAME)

            Properties propertiesObject = new Properties()
            propertiesObject.putAll(properties)
            File propertyFile = new File(getTemporaryDir(), "sonar-project.properties")
            GUtil.saveProperties(propertiesObject, propertyFile)

            List<String> args = []

            if(properties.containsKey('sonar.verbose') && Boolean.getBoolean(properties.get('sonar.verbose').toString()) ) {
                args.add('--debug')
                args.add('--errors')
            }

            return javaExec
                    .systemProperty("project.settings", propertyFile.getAbsolutePath())
                    .systemProperty("project.home", project.projectDir.getAbsolutePath())
                    .setClasspath(sonarRunnerConfiguration)
                    .setMain(MAIN_CLASS_NAME)
                    .setArgs(args)
        }
    }

    /**
     * Set Java fork options.
     *
     * @return JavaForkOptions
     */
    public JavaForkOptions getForkOptions() {
        if (forkOptions == null) {
            forkOptions = new DefaultJavaForkOptions(getFileResolver());
        }

        return forkOptions;
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }
}
