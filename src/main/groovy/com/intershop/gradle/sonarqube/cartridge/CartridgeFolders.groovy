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
package com.intershop.gradle.sonarqube.cartridge

import com.google.common.base.Predicate
import com.google.common.collect.Iterables

import groovy.util.logging.Slf4j

import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet

import static org.gradle.util.CollectionUtils.nonEmptyOrNull

/**
 * This is the folder configuration of a cartridge project.
 * It provides all available folders and for special
 * artifacts filetrees.
 */
@Slf4j
class CartridgeFolders {

    /**
     * Name of the static files folder
     */
    final static String STATICFILES_FOLDER = 'staticfiles'

    /**
     * Name of the old java source name
     */
    final static String JAVASOURCE_FOLDER = 'javasource'

    /**
     * Name of the folder which contains cartridge
     * specific files on the shared file system.
     */
    final static String SHARE_FOLDER = 'share'

    /**
     * Name of the folder with catridge specifc
     * files and directories
     */
    final static String CARTRIDGE_FOLDER = 'cartridge'

    /**
     * Special cartridge artifacts - pagelets (CMS)
     */
    final static String PAGELETS_FOLDER = 'pagelets'

    /**
     * Special cartridge artifacts - pipelines
     */
    final static String PIPELINES_FOLDER = 'pipelines'

    /**
     * Special cartridge artifacts - templates (isml)
     */
    final static String TEMPLATES_FOLDER = 'templates'

    /**
     * Special cartridge artifacts - static web content
     */
    final static String STATIC_FOLDER = 'static'

    /**
     * Special cartridge artifacts - queries
     */
    final static String QUERIES_FOLDER = 'queries'

    /**
     * Special cartridge artifacts - webforms
     */
    final static String WEBFORMS_FOLDER = 'webforms'

    /**
     * Special cartridge artifacts - components
     */
    final static String COMPONENTS_FOLDER = 'components'

    /**
     * Special cartridge artifacts - extensions
     */
    final static String EXTENSIONS_FOLDER = 'extensions'

    /**
     * Special cartridge artifacts - webservices configuration files
     */
    final static String WEBSERVICES_FOLDER = 'webservices'

    /**
     * Folder of WSDL files
     */
    final static String WSDL_FOLDER = 'wsdl'

    // project object
    private final Project project

    /**
     * Constructs the class based on the project
     */
    CartridgeFolders(Project project) {
        this.project = project
    }

    /**
     * Get file object with old folder with
     * java source files and resources
     */
    @Deprecated
    public File getJavasourceFolder() {
        return new File(project.projectDir, "${JAVASOURCE_FOLDER}")
    }

    /**
     * Get file object for folder with pagelet files
     */
    public File getPageletsFolder() {
        return new File(project.projectDir, "${STATICFILES_FOLDER}/${CARTRIDGE_FOLDER}/${PAGELETS_FOLDER}")
    }

    /**
     * Get file object for folder with templates files
     */
    public File getTemplatesFolder() {
        return new File(project.projectDir, "${STATICFILES_FOLDER}/${CARTRIDGE_FOLDER}/${TEMPLATES_FOLDER}")
    }

    /**
     * Get file object for folder with pipelines files
     */
    public File getPipelinesFolder() {
        return new File(project.projectDir, "${STATICFILES_FOLDER}/${CARTRIDGE_FOLDER}/${PIPELINES_FOLDER}")
    }

    /**
     * Get file object for folder with static webcontent files
     */
    public File getStaticFolder() {
        return new File(project.projectDir, "${STATICFILES_FOLDER}/${CARTRIDGE_FOLDER}/${STATIC_FOLDER}")
    }

    /**
     * Get file object for folder with query files
     */
    public File getQueriesFolder() {
        return new File(project.projectDir, "${STATICFILES_FOLDER}/${CARTRIDGE_FOLDER}/${QUERIES_FOLDER}")
    }

    /**
     * Get file object for folder with webform files
     */
    public File getWebformsFolder() {
        return new File(project.projectDir, "${STATICFILES_FOLDER}/${CARTRIDGE_FOLDER}/${WEBFORMS_FOLDER}")
    }

    /**
     * Get file object for folder with components files
     */
    public File getComponentsFolder() {
        return new File(project.projectDir, "${STATICFILES_FOLDER}/${CARTRIDGE_FOLDER}/${COMPONENTS_FOLDER}")
    }

    /**
     * Get file object for folder with extension files
     */
    public File getExtensionsFolder() {
        return new File(project.projectDir, "${STATICFILES_FOLDER}/${CARTRIDGE_FOLDER}/${EXTENSIONS_FOLDER}")
    }

    /**
     * Get file object for folder with webservice configuration files
     */
    public File getWebServicesFolder() {
        return new File(project.projectDir, "${STATICFILES_FOLDER}/${CARTRIDGE_FOLDER}/${WEBSERVICES_FOLDER}")
    }

    /**
     * Get file object for folder with WSDL files
     */
    public File getWSDLFolder() {
        return new File(project.projectDir, "${STATICFILES_FOLDER}/${WSDL_FOLDER}")
    }

    /**
     * Get file list of directories with java component sets
     */
    public List<File> getJavaSourceFolders() {
        List<File> srcDirectories = []

        // only available if java base plugin added
        project.plugins.withType(JavaBasePlugin) {
            JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class)

            // main source set
            SourceSet main = javaPluginConvention.getSourceSets().getAt('main')
            srcDirectories = nonEmptyOrNull(Iterables.filter(main.allSource.srcDirs, new Predicate<File>() {
                public boolean apply(File file) {
                    return file.exists()
                }
            }))
        }

        return srcDirectories
    }

    /**
     * Get file tree of pagelet files
     */
    public FileTree getPageletsFileTree() {
        return project.fileTree(pageletsFolder.absolutePath)
    }

    /**
     * Get file tree of template files (*.isml)
     */
    public FileTree getTemplatesFileTree() {
        return project.fileTree(templatesFolder.absolutePath){
            include '**/**/*.isml'
        }
    }

    /**
     * Get file tree of pipeline files (*.xml)
     */
    public FileTree getPipelinesFileTree() {
        return project.fileTree(pipelinesFolder.absolutePath){
            include '**/**/*.xml'
        }
    }

    /**
     * Get file tree of java script files (*.js)
     */
    public FileTree getJSFileTree() {
        return project.fileTree(staticFolder.absolutePath){
            include '**/**/*.js'
        }
    }

    /**
     * Get file tree of css files (*.css)
     */
    public FileTree getCSSFileTree() {
        return project.fileTree(staticFolder.absolutePath){
            include '**/**/*.css'
        }
    }

    /**
     * Get file tree of query files
     */
    public FileTree getQueriesFileTree() {
        return project.fileTree(queriesFolder.absolutePath)
    }

    /**
     * Get file tree of webform files
     */
    public FileTree getWebformsFileTree() {
        return project.fileTree(webformsFolder.absolutePath)
    }

    /**
     * Get file tree of pipelet files
     */
    public FileTree getPipeletsFileTree() {
        List<FileTree> trees = []
        List<File> sources = javaSourceFolders

        if(sources) {
            sources.each {File dir ->
                FileTree tree = project.fileTree(dir) {
                    include '**/pipelet/**/*.xml'
                }
                trees.add(tree)
            }
        }
        if(trees) {
            return trees.sum()
        }

        return null
    }

}
