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

import com.intershop.gradle.test.AbstractIntegrationSpec
import groovy.util.logging.Slf4j
import spock.lang.Requires
import spock.lang.Unroll

import java.nio.file.FileSystems

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED as SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS as SUCCESS

@Unroll
@Slf4j
class SonarQubeTaskSpec extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
        version = '1.0.0.0'

        subprojects {
            group = 'com.test'

            repositories {
                jcenter()
            }
        }
        """.stripIndent()
    }

    private String buildFileContentBase = """
                                          plugins {
                                              id 'java'
                                              id 'com.intershop.gradle.sonarqube'
                                          }

                                          version = '1.0.0.0'

                                          sourceCompatibility = 1.7
                                          targetCompatibility = 1.7
                                          """.stripIndent()

    /*
    Project with configured skipProject
     */
    private File createSubProject_sonarSkip(String projectPath, File settingsGradle) {
        File subProject = createSubProject(projectPath, settingsGradle,
        """
        ${buildFileContentBase}

        sonarqube {
            sonarProperties {
                property 'sonar.test', 'test'
                property 'sonar.verbose', 'true'
            }

            skipProject = true
        }
        """)

        writeJavaTestClass('com.intershop.skip', subProject)

        return subProject
    }

    private File createSubProject_empty(String projectPath, File settingsGradle) {
        File subProject = createSubProject(projectPath, settingsGradle,
                """
        ${buildFileContentBase}

        sonarqube {
            sonarProperties {
                property 'sonar.test', 'test'
                property 'sonar.verbose', 'true'
            }
        }
        """)

        return subProject
    }

    /*
    Project with configured jacoco
     */
    private File createSubProject_jacoco(String projectPath, File settingsGradle) {
        File subProject = createSubProject(projectPath, settingsGradle,
        """
        ${buildFileContentBase}

        apply plugin: "jacoco"

        sonarqube {
            sonarProperties {
                property 'sonar.test', 'test'
                property 'sonar.verbose', 'true'
            }
        }

        jacoco {
            toolVersion = "0.7.6.201602180812"
        }

        dependencies {
            testCompile 'junit:junit:4.12'
        }
        """)

        writeJavaTestClass('com.intershop.jacoco', subProject)
        writeJavaTestClassTest('com.intershop.jacoco', false, subProject)

        return subProject
    }

    /*
    Project with js module
     */
    private File createSubProject_multiModule(String projectPath, File settingsGradle) {
        File subProject = createSubProject(projectPath, settingsGradle,
        """
        ${buildFileContentBase}

        sonarqube {
            sonarProperties {
                property 'sonar.test', 'test'
                property 'sonar.modules', 'java, test'

                property 'test.sonar.projectName', 'Javascript'
                property 'test.sonar.sources', 'js'
                property 'test.sonar.projectBaseDir', projectDir.absolutePath
                property 'test.sonar.language', 'js'

                property 'sonar.verbose', 'true'
            }
        }
        """)

        writeJavaTestClass('com.intershop.multi', subProject)
        copyResources('cartridge/static/default/js/test','js/test', subProject)
        copyResources('cartridge/static/default/js/test','jscript/test', subProject)

        return subProject
    }

    /*
    Project with intershop artifacts
     */
    private File createSubProject_intershopComponent(String projectPath, File settingsGradle) {
        File subProject = createSubProject(projectPath, settingsGradle,
                """
        ${buildFileContentBase}
        sonarqube {
            sonarProperties {
                property 'sonar.test', 'test'
                property 'sonar.verbose', 'true'
            }
        }
        """)

        writeJavaTestClass('com.intershop.comp1', subProject)
        writeJavaTestClass('com.intershop.comp2', subProject)

        copyResources('cartridge','staticfiles/cartridge', subProject)
        copyResources('test-cartridge/javasource','test-cartridge/javasource/com/intershop/component', subProject)

        return subProject
    }

    /*
Project with intershop artifacts
 */
    private File createSubProject_intershopComponentChangedDefaults(String projectPath, File settingsGradle) {
        File subProject = createSubProject(projectPath, settingsGradle,
                """
        ${buildFileContentBase}
        sonarqube {
            modules = ['js','css']

            sonarProperties {
                property 'sonar.test', 'test'
                property 'sonar.verbose', 'true'
                property 'js.sonar.sources', 'staticfiles/cartridge/static/default/js'
                property 'js.sonar.projectBaseDir', project.projectDir
            }
        }
        """)

        writeJavaTestClass('com.intershop.comp1', subProject)
        writeJavaTestClass('com.intershop.comp2', subProject)

        copyResources('cartridge','staticfiles/cartridge', subProject)
        copyResources('test-cartridge/javasource','test-cartridge/javasource/com/intershop/component', subProject)

        return subProject
    }

    private Properties getSonarProjectProperties(File projectDir) {
        File propsFile = new File(projectDir, 'build/tmp/sonarqube/sonar-project.properties')
        Properties props = new Properties()
        propsFile.withInputStream {
            stream -> props.load(stream)
        }
        return props
    }

    @Requires({
        System.properties['sonarHostUrl']
    })
    def 'Test Intershop sonar - skip project #gradleVersion'(gradleVersion) {
        given:
        File settingsGradle = file('settings.gradle')

        createSubProject_sonarSkip('set_1:project_1a', settingsGradle)

        when:
        List<String> jvmArgs = ["-Dsonar.host.url=${System.properties['sonarHostUrl']}".toString()]

        List<String> args = ['sonarqube', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(jvmArgs + args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':set_1:project_1a:sonarqube').outcome == SKIPPED
        ! result.tasks.contains(':set_1:project_1b:sonarqube')

        where:
        gradleVersion << supportedGradleVersions
    }

    @Requires({
        System.properties['sonarHostUrl']
    })
    def 'Test Intershop sonar - empty sources #gradleVersion'(gradleVersion) {
        given:
        File settingsGradle = file('settings.gradle')

        createSubProject_empty('set_1:project_1a', settingsGradle)
        createSubProject_empty('set_1:project_1b', settingsGradle)

        when:
        List<String> jvmArgs = ["-Dsonar.host.url=${System.properties['sonarHostUrl']}".toString()]

        List<String> args = ['sonarqube', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(jvmArgs + args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        result.task(':set_1:project_1a:sonarqube').outcome == SUCCESS
        result.task(':set_1:project_1b:sonarqube').outcome == SUCCESS

        where:
        gradleVersion << supportedGradleVersions
    }

    @Requires({
        System.properties['sonarHostUrl']
    })
    def 'Test Intershop sonar - jacoco #gradleVersion'(gradleVersion) {
        given:
        File settingsGradle = file('settings.gradle')

        createSubProject_sonarSkip('set_1:project_1a', settingsGradle)
        File pJacoco = createSubProject_jacoco('set_1:project_1b', settingsGradle)

        when:
        List<String> jvmArgs = ["-Dsonar.host.url=${System.properties['sonarHostUrl']}".toString()]

        List<String> args = ['sonarqube', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(jvmArgs + args)
                .withGradleVersion(gradleVersion)
                .build()

        Properties props = getSonarProjectProperties(pJacoco)

        then:
        ! result.tasks.contains(':set_1:project_1a:sonarqube')
        result.task(':set_1:project_1b:sonarqube').outcome == SUCCESS

        // check properties
        props.getProperty('sonar.analysis.mode') == 'preview'
        props.getProperty('sonar.importSources') == 'false'
        props.getProperty('sonar.test') == 'test'

        props.getProperty('sonar.modules').split(',').size() == 1
        props.getProperty('sonar.modules').split(',').contains('java')

        //check sonar scan
        result.output.contains('INFO: EXECUTION SUCCESS')
        ! result.output.contains('INFO: EXECUTION FAILED')
        ! result.output.contains('Scan Javascript')
        ! result.output.contains('Scan CSS')

        result.output.contains('Sensor JaCoCoSensor')
        result.output.contains("jacoco${FileSystems.getDefault().getSeparator()}test.exec")

        where:
        gradleVersion << supportedGradleVersions
    }

    @Requires({
        System.properties['sonarHostUrl']
    })
    def 'Test Intershop sonar - additional module #gradleVersion'(gradleVersion) {
        given:
        File settingsGradle = file('settings.gradle')
        File subProject = createSubProject_multiModule(':set_2:project_2a', settingsGradle)

        when:
        List<String> jvmArgs = ["-Dsonar.host.url=${System.properties['sonarHostUrl']}".toString()]

        List<String> args = ['sonarqube', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(jvmArgs + args)
                .withGradleVersion(gradleVersion)
                .build()

        Properties props = getSonarProjectProperties(subProject)

        then:
        ! result.tasks.contains(':set_2:project_2b:sonarqube')
        result.task(':set_2:project_2a:sonarqube').outcome == SUCCESS

        // check properties
        props.getProperty('sonar.modules').split(',').size() == 2
        props.getProperty('sonar.modules').split(',').each {
            print("|" + it + "|")
        }
        props.getProperty('sonar.modules').split(',').contains('test')
        props.getProperty('sonar.modules').split(',').contains('java')
        props.getProperty('test.sonar.language') == 'js'
        props.getProperty('test.sonar.sources') == 'js'

        props.getProperty('sonar.test') == 'test'

        //check sonar scan
        result.output.contains('INFO: EXECUTION SUCCESS')
        ! result.output.contains('INFO: EXECUTION FAILED')
        result.output.contains('Scan Javascript')
        ! result.output.contains('Scan CSS')

        ! result.output.contains('Sensor JaCoCoOverallSensor...')

        when:
        File newBuildFile = new File(subProject, "build.gradle")
        newBuildFile.delete()

        newBuildFile << """
        ${buildFileContentBase}

        sonarqube {
            sonarProperties {
                property 'sonar.test', 'test'
                property 'sonar.modules', 'test'

                property 'test.sonar.projectName', 'Javascript'
                property 'test.sonar.sources', 'jscript'
                property 'test.sonar.projectBaseDir', projectDir.absolutePath
                property 'test.sonar.language', 'js'

                property 'sonar.verbose', 'true'
            }
        }
        """

        def newResult = getPreparedGradleRunner()
                .withArguments(jvmArgs + args)
                .withGradleVersion(gradleVersion)
                .build()

        Properties newProps = getSonarProjectProperties(subProject)

        then:
        newProps.getProperty('sonar.modules').split(',').contains('test')
        newProps.getProperty('test.sonar.sources') == 'jscript'

        where:
        gradleVersion << supportedGradleVersions
    }

    @Requires({
        System.properties['sonarHostUrl']
    })
    def 'Test Intershop sonar - intershop component #gradleVersion'(gradleVersion) {
        given:
        File settingsGradle = file('settings.gradle')
        File subProject = createSubProject_intershopComponent(':set_2:project_2b', settingsGradle)

        when:
        List<String> jvmArgs = ["-Dsonar.host.url=${System.properties['sonarHostUrl']}".toString()]

        List<String> args = ['sonarqube', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(jvmArgs + args)
                .withGradleVersion(gradleVersion)
                .build()

        Properties props = getSonarProjectProperties(subProject)

        then:
        ! result.tasks.contains(':set_2:project_2a:sonarqube')
        result.task(':set_2:project_2b:sonarqube').outcome == SUCCESS

        // check properties
        props.getProperty('sonar.modules').split(',').size() == 3
        props.getProperty('sonar.modules').split(',').contains('css')
        props.getProperty('sonar.modules').split(',').contains('java')
        props.getProperty('sonar.modules').split(',').contains('js')

        props.getProperty('sonar.test') == 'test'

        //check sonar scan
        result.output.contains('INFO: EXECUTION SUCCESS')
        ! result.output.contains('INFO: EXECUTION FAILED')
        result.output.contains('Scan Javascript')
        result.output.contains('Scan CSS')

        ! result.output.contains('Sensor JaCoCoOverallSensor...')
        ! result.output.contains('Sensor JaCoCoSensor...')

        where:
        gradleVersion << supportedGradleVersions
    }

    @Requires({
        System.properties['sonarHostUrl']
    })
    def 'Test Intershop sonar - intershop component with changed defaults #gradleVersion'(gradleVersion) {
        given:
        File settingsGradle = file('settings.gradle')
        File subProject = createSubProject_intershopComponentChangedDefaults(':set_2:project_2b', settingsGradle)

        when:
        List<String> jvmArgs = ["-Dsonar.host.url=${System.properties['sonarHostUrl']}".toString()]

        List<String> args = ['sonarqube', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(jvmArgs + args)
                .withGradleVersion(gradleVersion)
                .build()

        Properties props = getSonarProjectProperties(subProject)

        then:
        ! result.tasks.contains(':set_2:project_2a:sonarqube')
        result.task(':set_2:project_2b:sonarqube').outcome == SUCCESS

        // check properties
        props.getProperty('sonar.modules').split(',').size() == 2
        props.getProperty('sonar.modules').split(',').contains('css')
        props.getProperty('sonar.modules').split(',').contains('js')

        props.getProperty('js.sonar.sources').endsWith('staticfiles/cartridge/static/default/js')

        where:
        gradleVersion << supportedGradleVersions
    }


    @Requires({
        System.properties['sonarHostUrl']
    })
    def 'Test Intershop sonar - offline #gradleVersion'(gradleVersion) {
        given:
        File settingsGradle = file('settings.gradle')
        createSubProject_sonarSkip('set_1:project_1a', settingsGradle)
        createSubProject_jacoco('set_1:project_1b', settingsGradle)
        createSubProject_multiModule(':set_2:project_2a', settingsGradle)
        createSubProject_intershopComponent(':set_2:project_2b', settingsGradle)

        when:
        List<String> args = ['sonarqube', '-PskipSonar=true' , '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        then:
        // project 1a skipProject is true
        result.task(':set_1:project_1a:sonarqube').outcome == SKIPPED
        result.task(':set_1:project_1b:sonarqube').outcome == SKIPPED
        result.task(':set_2:project_2a:sonarqube').outcome == SKIPPED
        result.task(':set_2:project_2b:sonarqube').outcome == SKIPPED

        ! result.output.contains('INFO: EXECUTION FAILED')

        where:
        gradleVersion << supportedGradleVersions
    }

    @Requires({
        System.properties['sonarHostUrl']
    })
    def 'Test Intershop sonar with multiproject configuration - overall #gradleVersion'(gradleVersion) {
        given:
        File settingsGradle = file('settings.gradle')
        File prjMulti = createSubProject_multiModule(':set_2:project_2a', settingsGradle)
        File prjIntershop = createSubProject_intershopComponent(':set_2:project_2b', settingsGradle)

        when:
        List<String> jvmArgs = ["-Dsonar.host.url=${System.properties['sonarHostUrl']}".toString()]

        List<String> args = ['sonarqube', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(jvmArgs + args)
                .withGradleVersion(gradleVersion)
                .build()

        Properties propsMulti = getSonarProjectProperties(prjMulti)
        Properties propsIntershop = getSonarProjectProperties(prjIntershop)

        then:
        result.task(':set_2:project_2a:sonarqube').outcome == SUCCESS
        result.task(':set_2:project_2b:sonarqube').outcome == SUCCESS

        propsMulti.getProperty('sonar.modules').split(',').contains('test')
        propsMulti.getProperty('sonar.modules').split(',').contains('java')
        propsMulti.getProperty('test.sonar.language') == 'js'
        propsMulti.getProperty('sonar.test') == 'test'

        propsIntershop.getProperty('sonar.modules').split(',').contains('css')
        propsIntershop.getProperty('sonar.modules').split(',').contains('java')
        propsIntershop.getProperty('sonar.modules').split(',').contains('js')
        propsIntershop.getProperty('sonar.test') == 'test'

        result.output.contains('INFO: EXECUTION SUCCESS')
        ! result.output.contains('INFO: EXECUTION FAILED')
        result.output.contains('Scan Javascript')
        result.output.contains('Scan CSS')

        where:
        gradleVersion << supportedGradleVersions
    }

    @Requires({
        System.properties['sonarHostUrl']
    })
    def 'Test intershop sonar with multiproject configuration - overall with gradle.properties #gradleVersion'(gradleVersion) {
        given:
        File settingsGradle = file('settings.gradle')
        File prjMulti = createSubProject_multiModule(':set_2:project_2a', settingsGradle)
        File prjIntershop = createSubProject_intershopComponent(':set_2:project_2b', settingsGradle)

        file('gradle.properties') << """
            systemProp.sonar.host.url = ${System.properties['sonarHostUrl']}
        """.stripIndent()

        when:
        List<String> args = ['sonarqube', '-s', '-i']

        def result = getPreparedGradleRunner()
                .withArguments(args)
                .withGradleVersion(gradleVersion)
                .build()

        Properties propsMulti = getSonarProjectProperties(prjMulti)
        Properties propsIntershop = getSonarProjectProperties(prjIntershop)

        then:
        result.task(':set_2:project_2a:sonarqube').outcome == SUCCESS
        result.task(':set_2:project_2b:sonarqube').outcome == SUCCESS

        propsMulti.getProperty('sonar.modules').split(',').contains('test')
        propsMulti.getProperty('sonar.modules').split(',').contains('java')
        propsMulti.getProperty('test.sonar.language') == 'js'
        propsMulti.getProperty('sonar.test') == 'test'

        propsIntershop.getProperty('sonar.modules').split(',').contains('css')
        propsIntershop.getProperty('sonar.modules').split(',').contains('java')
        propsIntershop.getProperty('sonar.modules').split(',').contains('js')
        propsIntershop.getProperty('sonar.test') == 'test'

        result.output.contains('INFO: EXECUTION SUCCESS')
        ! result.output.contains('INFO: EXECUTION FAILED')
        result.output.contains('Scan Javascript')
        result.output.contains('Scan CSS')

        where:
        gradleVersion << supportedGradleVersions
    }
}
