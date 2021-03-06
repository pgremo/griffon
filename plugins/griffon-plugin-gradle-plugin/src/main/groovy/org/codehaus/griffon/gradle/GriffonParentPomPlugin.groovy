/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2008-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.griffon.gradle

import groovy.transform.CompileDynamic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.kordamp.gradle.plugin.base.ProjectConfigurationExtension
import org.kordamp.gradle.plugin.bintray.BintrayPlugin
import org.kordamp.gradle.plugin.bom.BomPlugin
import org.kordamp.gradle.plugin.project.java.JavaProjectPlugin

/**
 * @author Andres Almiray
 */
class GriffonParentPomPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.plugins.apply(JavaProjectPlugin)
        project.plugins.apply(BintrayPlugin)
        project.plugins.apply(BomPlugin)

        if (!project.hasProperty('bintrayUsername')) project.ext.bintrayUsername = '**undefined**'
        if (!project.hasProperty('bintrayApiKey')) project.ext.bintrayApiKey = '**undefined**'
        if (!project.hasProperty('sonatypeUsername')) project.ext.sonatypeUsername = '**undefined**'
        if (!project.hasProperty('sonatypePassword')) project.ext.sonatypePassword = '**undefined**'

        String guideProjectName = project.rootProject.name - '-plugin' + '-guide'

        project.extensions.findByType(ProjectConfigurationExtension).with {
            release = (project.rootProject.project.findProperty('release') ?: false).toBoolean()

            info {
                vendor = 'Griffon'

                links {
                    website = "https://github.com/griffon-plugins/${project.rootProject.name}"
                    issueTracker = "https://github.com/griffon-plugins/${project.rootProject.name}/issues"
                    scm = "https://github.com/griffon-plugins/${project.rootProject.name}.git"
                }

                scm {
                    url = "https://github.com/griffon-plugins/${project.rootProject.name}"
                    connection = "scm:git:https://github.com/griffon-plugins/${project.rootProject.name}.git"
                    developerConnection = "scm:git:git@github.com:griffon-plugins/${project.rootProject.name}.git"
                }

                people {
                    person {
                        id = 'aalmiray'
                        name = 'Andres Almiray'
                        url = 'http://andresalmiray.com/'
                        roles = ['developer']
                        properties = [
                            twitter: 'aalmiray',
                            github : 'aalmiray'
                        ]
                    }
                }

                credentials {
                    sonatype {
                        username = project.sonatypeUsername
                        password = project.sonatypePassword
                    }
                }

                repositories {
                    repository {
                        name = 'localRelease'
                        url = "${project.rootProject.buildDir}/repos/local/release"
                    }
                    repository {
                        name = 'localSnapshot'
                        url = "${project.rootProject.buildDir}/repos/local/snapshot"
                    }
                }
            }

            licensing {
                licenses {
                    license {
                        id = 'Apache-2.0'
                    }
                }
            }

            docs {
                javadoc {
                    excludes = ['**/*.html', 'META-INF/**']
                }
                sourceXref {
                    inputEncoding = 'UTF-8'
                }
            }

            bintray {
                enabled = true
                credentials {
                    username = project.bintrayUsername
                    password = project.bintrayApiKey
                }
                userOrg = 'griffon'
                repo = 'griffon-plugins'
                name = project.rootProject.name
                publish = (project.rootProject.project.findProperty('release') ?: false).toBoolean()
            }

            publishing {
                releasesRepository = 'localRelease'
                snapshotsRepository = 'localSnapshot'
            }

            bom {
                exclude(guideProjectName)
            }

            dependencies {
                dependency("org.kordamp.gipsy:gipsy:${project.jipsyVersion}")
                dependency("org.kordamp.jipsy:jipsy:${project.jipsyVersion}") {
                    modules    = ['jipsy-util']
                }
                dependency("junit:junit:${project.junitVersion}")
                dependency('junit5') {
                    groupId    = 'org.junit.jupiter'
                    artifactId = 'junit-jupiter-api'
                    version    = project.junit5Version
                    modules    = [
                        'junit-jupiter-params',
                        'junit-jupiter-engine'
                    ]
                }
                dependency('junit5v') {
                    groupId    = 'org.junit.vintage'
                    artifactId = 'junit-vintage-engine'
                    version    = project.junit5Version
                }
            }
        }

        project.allprojects {
            repositories {
                jcenter()
                mavenCentral()
                mavenLocal()
            }

            normalization {
                runtimeClasspath {
                    ignore('/META-INF/MANIFEST.MF')
                }
            }

            dependencyUpdates.resolutionStrategy {
                componentSelection { rules ->
                    rules.all { selection ->
                        boolean rejected = ['alpha', 'beta', 'rc', 'cr'].any { qualifier ->
                            selection.candidate.version ==~ /(?i).*[.-]${qualifier}[.\d-]*.*/
                        }
                        if (rejected) {
                            selection.reject('Release candidate')
                        }
                    }
                }
            }
        }

        project.allprojects { Project p ->
            def scompat = project.project.findProperty('sourceCompatibility')
            def tcompat = project.project.findProperty('targetCompatibility')

            p.tasks.withType(JavaCompile) { JavaCompile c ->
                if (scompat) c.sourceCompatibility = scompat
                if (tcompat) c.targetCompatibility = tcompat
            }
            p.tasks.withType(GroovyCompile) { GroovyCompile c ->
                if (scompat) c.sourceCompatibility = scompat
                if (tcompat) c.targetCompatibility = tcompat
            }
        }

        project.subprojects { sub ->
            sub.pluginManager.withPlugin('java-base', new Action<AppliedPlugin>() {
                @Override
                void execute(AppliedPlugin ap) {
                    sub.dependencies {
                        compileOnly sub.config.dependencies.gav('gipsy')
                        compileOnly sub.config.dependencies.gav('jipsy')
                        annotationProcessor sub.config.dependencies.gav('jipsy')

                        testImplementation sub.config.dependencies.gav('junit5', 'junit-jupiter-api')
                        testImplementation sub.config.dependencies.gav('junit5', 'junit-jupiter-params')
                        testRuntimeOnly sub.config.dependencies.gav('junit5', 'junit-jupiter-engine')
                        testRuntimeOnly(sub.config.dependencies.gav('junit5v', 'junit-vintage-engine')) {
                            exclude group: 'junit', module: 'junit'
                        }
                        testImplementation(sub.config.dependencies.gav('junit')) {
                            exclude group: 'org.hamcrest', module: 'hamcrest-core'
                        }
                        testImplementation("org.codehaus.groovy:groovy-all:${sub.groovyVersion}") {
                            exclude group: 'junit', module: 'junit'
                        }
                        testImplementation("org.spockframework:spock-core:${sub.spockVersion}") {
                            exclude group: 'junit', module: 'junit'
                            exclude group: 'org.codehaus.groovy', module: 'groovy-all'
                        }

                        compileOnly "org.codehaus.griffon:griffon-core-compile:${sub.griffonVersion}"
                        compileOnly "org.codehaus.griffon:griffon-beans-compile:${sub.griffonVersion}"

                        api "org.codehaus.griffon:griffon-core:${sub.griffonVersion}"

                        testCompileOnly "org.codehaus.griffon:griffon-core-compile:${sub.griffonVersion}"
                        testCompileOnly "org.codehaus.griffon:griffon-beans-compile:${sub.griffonVersion}"
                        testCompileOnly "org.codehaus.griffon:griffon-groovy-compile:${sub.griffonVersion}"
                        testCompileOnly sub.config.dependencies.gav('gipsy')

                        testImplementation "org.codehaus.griffon:griffon-core-test:${sub.griffonVersion}"
                        testImplementation("org.codehaus.griffon:griffon-groovy:${sub.griffonVersion}") {
                            exclude group: 'org.codehaus.groovy', module: 'groovy-all'
                        }

                        testRuntimeOnly "org.codehaus.griffon:griffon-guice:${sub.griffonVersion}"
                        testRuntimeOnly "org.slf4j:slf4j-simple:${sub.slf4jVersion}"
                    }

                    sub.test {
                        useJUnitPlatform()
                    }

                    sub.jar {
                        manifest {
                            attributes('Automatic-Module-Name': sub.group + (sub.name - 'griffon-').replace('-', '.'))
                        }
                    }
                }
            })
        }

        project.projects {
            subprojects {
                dir('subprojects') {
                    config {
                        info {
                            name        = project.name
                            description = project.projectDescription
                        }
                    }

                    compileGroovy.enabled = false
                }

                path(':' + guideProjectName) {
                    ext.projectDependencies = []

                    asciidoctor {
                        baseDirFollowsSourceDir()
                        attributes = [
                            toc                    : 'left',
                            doctype                : 'book',
                            icons                  : 'font',
                            encoding               : 'utf-8',
                            sectlink               : true,
                            sectanchors            : true,
                            numbered               : true,
                            linkattrs              : true,
                            imagesdir              : 'images',
                            linkcss                : true,
                            stylesheet             : 'css/style.css',
                            'source-highlighter'   : 'coderay',
                            'coderay-linenums-mode': 'table',
                            'griffon-version'      : rootProject.griffonVersion
                        ]

                        sources {
                            include 'index.adoc'
                        }

                        resources {
                            from file('src/resources')
                        }
                    }

                    guide {
                        sourceHtmlDir = 'api-src'
                    }
                }
            }
        }

        project.afterEvaluate {
            project.tasks.named('aggregateJavadoc', Javadoc,
                new Action<Javadoc>() {
                    @Override
                    @CompileDynamic
                    void execute(Javadoc t) {try{
                        t.classpath = t.project.findProject(':' + guideProjectName).ext.projectDependencies.collect { projectName ->
                            [t.project.findProject(projectName).sourceSets.main.output,
                             t.project.findProject(projectName).configurations.compile,
                             t.project.findProject(projectName).configurations.compileOnly]
                        }.flatten().sum() as FileCollection

                        t.options.overview    = t.project.findProject(':' + guideProjectName).file('src/javadoc/overview.html')
                        t.options.links       = ['https://www.slf4j.org/apidocs/',
                                               'https://junit.org/junit4/javadoc/latest/',
                                               'https://javax-inject.github.io/javax-inject/api/',
                                               "https://griffon-framework.org/guide/${t.project.griffonVersion}/api/".toString()]

                        t.doLast { task ->
                            t.project.copy {
                                into task.destinationDir
                                from t.project.findProject(':' + guideProjectName).file('src/javadoc/resources/img/griffon.ico'),
                                    t.project.findProject(':' + guideProjectName).file('src/javadoc/resources/css/stylesheet.css')
                            }
                            t.project.copy {
                                into t.project.file("${task.destinationDir}/resources")
                                from t.project.findProject(':' + guideProjectName).file('src/javadoc/resources/img/')
                            }
                        }}catch(Exception x){x.printStackTrace()}
                    }
                })
        }
    }
}
