/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.bsc.maven.plugin.findclass;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.bsc.functional.F;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pyx4j.log4j.MavenLogAppender;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Finds duplicate classes/resources.
 *
 * @see
 * <a href="http://docs.codehaus.org/display/MAVENUSER/Mojo+Developer+Cookbook">Mojo
 * Developer Cookbook</a>
 * @author bsorrentino
 */
@Mojo(name = "find",
        requiresProject = true,
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.TEST)
public class ClassFinderMojo extends AbstractMojo {

    protected final Logger LOG = LoggerFactory.getLogger(this.getClass());

    /**
     * The maven project (effective pom).
     */
    @Component
    private MavenProject project;

    /**
     * Whether the mojo should use the default resource ignore list.
     *
     * @parameter default-value="true"
     */
    @Parameter(defaultValue = "true")
    boolean useDefaultResourceIgnoreList = true;

    /**
     * Additional resources that should be ignored.
     */
    @Parameter(alias = "ignoredResources")
    private String[] ignoredResources;

    /**
     * A set of dependecies that should be completely ignored in the check.
     */
    @Parameter(property = "ignoredDependencies")
    DependencyWrapper[] ignoredDependencies;

    /**
     * Check the compile classpath. On by default.
     */
    @Parameter(defaultValue = "true")
    boolean checkCompileClasspath = true;

    /**
     * Check the runtime classpath. On by default.
     */
    @Parameter(defaultValue = "true")
    boolean checkRuntimeClasspath = true;

    /**
     * Check the test classpath. On by default.
     */
    @Parameter(defaultValue = "true")
    boolean checkTestClasspath = true;

    /**
     * Skip the plugin execution.
     *
     * <pre>
     *   <configuration>
     *     <skip>true</skip>
     *   </configuration>
     * </pre>
     *
     */
    @Parameter(defaultValue = "false")
    protected boolean skip = false;

    /**
     * Simple class name
     *
     */
    @Parameter(alias = "className", property = "className")
    private String className;

    /**
     *
     * @param ignoredDependencies
     * @throws InvalidVersionSpecificationException
     */
    public void setIgnoredDependencies(Dependency[] ignoredDependencies) throws InvalidVersionSpecificationException {
        this.ignoredDependencies = new DependencyWrapper[ignoredDependencies.length];
        for (int idx = 0; idx < ignoredDependencies.length; idx++) {
            this.ignoredDependencies[idx] = new DependencyWrapper(ignoredDependencies[idx]);
        }
    }

    @Override
    public void execute() throws MojoExecutionException {
        MavenLogAppender.startPluginLog(this);

        try {
            if (skip) {
                LOG.debug("Skipping execution!");
            } else {
                if (checkCompileClasspath) {
                    checkCompileClasspath();
                }
                if (checkRuntimeClasspath) {
                    checkRuntimeClasspath();
                }
                if (checkTestClasspath) {
                    checkTestClasspath();
                }
            }
        } finally {
            MavenLogAppender.endPluginLog(this);
        }
    }

    private <T> void forEach(Collection<T> list, F<T> functor) {

        for (T e : list) {

            functor.f(e);
        }
    }

    private Map<File, Artifact> checkClasspath(List<Artifact> artifactList,
            List<String> classpathElementList) throws MojoExecutionException, DependencyResolutionRequiredException {

        final Map<File, Artifact> artifactsByFile = createArtifactsByFileMap(artifactList);

        addOutputDirectory(artifactsByFile);

        final ClasspathDescriptor classpathDesc = createClasspathDescriptor(classpathElementList);

        final Set<String> classNameList = classpathDesc.getClasss();
        forEach(classNameList, new F<String>() {

            @Override
            public void f(String p) {
                getLog().debug(String.format("evaluate class[%s]", p));
                if (className != null && p.endsWith(className)) {

                    getLog().info(String.format("FOUND: %s", p));

                    final Set<File> sourceSet = classpathDesc.getElementsHavingClass(p);

                    for (File f : sourceSet) {
                        getLog().info(String.format("\tsource: %s", f.getPath()));
                    }

                }
            }

        });

        return artifactsByFile;

    }

    @SuppressWarnings("unchecked")
    private void checkCompileClasspath() throws MojoExecutionException {
        try {
            LOG.info("Checking compile classpath");
            checkClasspath(project.getCompileArtifacts(), project.getCompileClasspathElements());
        } catch (DependencyResolutionRequiredException ex) {
            throw new MojoExecutionException("Could not resolve dependencies", ex);
        }

    }

    @SuppressWarnings("unchecked")
    private void checkRuntimeClasspath() throws MojoExecutionException {
        try {
            LOG.info("Checking runtime classpath");
            checkClasspath(project.getRuntimeArtifacts(), project.getRuntimeClasspathElements());
        } catch (DependencyResolutionRequiredException ex) {
            throw new MojoExecutionException("Could not resolve dependencies", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private void checkTestClasspath() throws MojoExecutionException {
        try {
            LOG.info("Checking test classpath");

            Map<File, Artifact> artifactsByFile = checkClasspath(project.getTestArtifacts(), project.getTestClasspathElements());
            addOutputDirectory(artifactsByFile);
        } catch (DependencyResolutionRequiredException ex) {
            throw new MojoExecutionException("Could not resolve dependencies", ex);
        }
    }

    private ClasspathDescriptor createClasspathDescriptor(List<String> classpathElements) throws MojoExecutionException {
        ClasspathDescriptor classpathDesc = new ClasspathDescriptor();

        classpathDesc.setUseDefaultResourceIgnoreList(useDefaultResourceIgnoreList);
        classpathDesc.setIgnoredResources(ignoredResources);

        if (classpathElements != null) {
            for (String element : classpathElements) {

                try {
                    classpathDesc.add(new File(element));
                } catch (FileNotFoundException ex) {
                    LOG.debug("Could not access classpath element " + element);
                } catch (IOException ex) {
                    throw new MojoExecutionException("Error trying to access element " + element, ex);
                }
            }
        }
        return classpathDesc;
    }

    private Map<File, Artifact> createArtifactsByFileMap(List<Artifact> artifacts) throws DependencyResolutionRequiredException {
        final Map<File, Artifact> artifactsByFile = new HashMap<>(artifacts.size());

        for (Artifact artifact : artifacts) {

            final File localPath = getLocalProjectPath(artifact);
            final File repoPath = artifact.getFile();

            if ((localPath == null) && (repoPath == null)) {
                throw new DependencyResolutionRequiredException(artifact);
            }
            if (localPath != null) {
                artifactsByFile.put(localPath, artifact);
            }
            if (repoPath != null) {
                artifactsByFile.put(repoPath, artifact);
            }
        }

        return artifactsByFile;
    }

    private File getLocalProjectPath(Artifact artifact) throws DependencyResolutionRequiredException {
        String refId = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
        MavenProject owningProject = (MavenProject) project.getProjectReferences().get(refId);

        if (owningProject != null) {
            if (artifact.getType().equals("test-jar")) {
                File testOutputDir = new File(owningProject.getBuild().getTestOutputDirectory());

                if (testOutputDir.exists()) {
                    return testOutputDir;
                }
            } else {
                return new File(project.getBuild().getOutputDirectory());
            }
        }
        return null;
    }

    private void addOutputDirectory(Map<File, Artifact> artifactsByFile) {
        File outputDir = new File(project.getBuild().getOutputDirectory());

        if (outputDir.exists()) {
            artifactsByFile.put(outputDir, null);
        }
    }

}
