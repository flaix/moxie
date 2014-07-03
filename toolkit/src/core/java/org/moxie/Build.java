/*
 * Copyright 2012 James Moger
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
package org.moxie;

import static java.text.MessageFormat.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.moxie.Toolkit.Key;
import org.moxie.console.Console;
import org.moxie.maxml.MaxmlException;
import org.moxie.utils.DeepCopier;
import org.moxie.utils.FileUtils;
import org.moxie.utils.StringUtils;


/**
 * Build is a container class for the effective build configuration, the console,
 * and the solver.
 */
public class Build {

	private final BuildConfig config;
	private final Console console;
	private final Solver solver;
	private final Date buildDate;

	public Build(File configFile, File basedir) throws MaxmlException, IOException {
		this.config = new BuildConfig(configFile, basedir);

		this.console = new Console(config.isColor());
		this.console.setDebug(config.isDebug());

		this.solver = new Solver(console, config);
		this.buildDate = new Date();
	}

	@Override
	public int hashCode() {
		return config.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Build) {
			return config.getProjectConfig().file.equals(((Build) o).getConfig().getProjectConfig().file);
		}
		return false;
	}

	public Date getBuildDate() {
		return buildDate;
	}

	public String getBuildDateString() {
		return new SimpleDateFormat("yyyy-MM-dd").format(buildDate);
	}

	public String getBuildTimestamp() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(buildDate);
	}

	public Date getReleaseDate() {
		return config.getPom().releaseDate;
	}

	public String getReleaseDateString() {
		if (config.getPom().releaseDate != null) {
			return new SimpleDateFormat("yyyy-MM-dd").format(config.getPom().releaseDate);
		}
		return null;
	}

	public Solver getSolver() {
		return solver;
	}

	public BuildConfig getConfig() {
		return config;
	}

	public Console getConsole() {
		return console;
	}

	public Pom getPom() {
		return config.getPom();
	}

	public Pom getPom(List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return getPom();
		}
		Pom pom = DeepCopier.copy(config.getPom());
		pom.clearDependencies();
		for (Dependency dep : config.getPom().getDependencies(false)) {
			for (String tag : tags) {
				if (dep.tags.contains(tag.toLowerCase())) {
					if (dep.ring == Constants.RING1) {
						pom.addDependency(dep, dep.definedScope);
					}
				}
			}
		}
		return pom;
	}

	public File getBuildArtifact(String classifier) {
		String name = config.getPom().artifactId;
		if (!StringUtils.isEmpty(config.getPom().version)) {
			name += "-" + config.getPom().version;
		}
		if (StringUtils.isEmpty(classifier)) {
			classifier = config.getPom().classifier;
		}
		if (!StringUtils.isEmpty(classifier)) {
			name += "-" + classifier;
		}
		return new File(getConfig().getTargetDirectory(), name + ".jar");
	}

	public void setup() {
		if (config.getRepositories().isEmpty()) {
			console.warn("No dependency repositories have been defined!");
		}

		solver.updateRepositoryMetadata();

		boolean solutionBuilt = solver.solve();
		ToolkitConfig project = config.getProjectConfig();
		// create apt source directories
		for (SourceDirectory sd : config.getSourceDirectories()) {
			if (sd.apt) {
				sd.getSources().mkdirs();
			}
		}

		if (project.apply.size() > 0) {
			console.separator();
			console.log("apply");
			boolean applied = false;

			// create/update Eclipse configuration files
			if (solutionBuilt && (project.getEclipseSettings() != null)) {
				EclipseSettings settings = project.getEclipseSettings();
				writeEclipseFactorypath(settings);
				writeEclipseClasspath(settings);
				writeEclipseProject(settings);
				console.notice(1, "rebuilt Eclipse configuration");
				applied = true;
			}

            // create/update IntelliJ IDEA configuration files
            if (solutionBuilt && (project.getIntelliJSettings() != null)) {
            	IntelliJSettings settings = project.getIntelliJSettings();
           		writeIntelliJProject(settings);
           		writeIntelliJAnt();
                writeIntelliJClasspath(settings);
                console.notice(1, "rebuilt IntelliJ IDEA configuration");
                applied = true;
            }

			// create/update Maven POM
			if (solutionBuilt && project.apply(Toolkit.APPLY_POM)) {
				writePOM();
				console.notice(1, "rebuilt pom.xml");
				applied = true;
			}

			if (!applied) {
				console.log(1, "nothing applied");
			}
		}
	}

	private File getIDEOutputFolder(Scope scope) {
		File baseFolder = new File(config.getProjectDirectory(), "bin");
		if (scope == null) {
			return baseFolder;
		}
		switch (scope) {
		case test:
			return new File(baseFolder, "test-classes");
		default:
			return new File(baseFolder, "classes");
		}
	}

	private void writeEclipseClasspath(EclipseSettings settings) {
		if (config.getSourceDirectories().isEmpty()
    			|| config.getPom().isPOM()
    			|| !config.getModules().isEmpty()) {
    		// no classpath to write
    		return;
    	}

		File projectFolder = config.getProjectDirectory();

		String genSrcDir = null;
		List<SourceDirectory> sourceDirs = new ArrayList<SourceDirectory>();
		sourceDirs.addAll(config.getProjectConfig().getSourceDirectories());
		sourceDirs.addAll(config.getProjectConfig().getResourceDirectories());
		StringBuilder sb = new StringBuilder();
		for (SourceDirectory sourceFolder : sourceDirs) {
			if (Scope.site.equals(sourceFolder.scope)) {
				continue;
			}
			String srcPath = FileUtils.getRelativePath(projectFolder, sourceFolder.getSources());
			if (sourceFolder.scope.isDefault()) {
				if (sourceFolder.apt) {
					// defined apt generated source folder
					genSrcDir = srcPath;
				} else {
					// defined standard source folder
					sb.append(format("<classpathentry kind=\"src\" path=\"{0}\" />\n", srcPath));
				}
			} else {
				// defined source folder, not compile-scoped (i.e. test)
				sb.append(format("<classpathentry kind=\"src\" path=\"{0}\" output=\"{1}\" />\n", srcPath, FileUtils.getRelativePath(projectFolder, getIDEOutputFolder(sourceFolder.scope))));
			}
		}

		// determine how to output dependencies (fixed-path or variable-relative)
		String kind = settings.var ? "var" : "lib";
		boolean extRelative = getConfig().getProjectConfig().dependencyDirectory != null && getConfig().getProjectConfig().dependencyDirectory.exists();

		// always link classpath against Moxie artifact cache
		Set<Dependency> dependencies = solver.solve(Scope.test);
		for (Dependency dependency : dependencies) {
			if (dependency instanceof SystemDependency) {
				SystemDependency sys = (SystemDependency) dependency;
				sb.append(format("<classpathentry kind=\"lib\" path=\"{0}\" />\n", FileUtils.getRelativePath(projectFolder, new File(sys.path))));
			} else {
				File jar = solver.getMoxieCache().getArtifact(dependency, dependency.extension);
				Dependency sources = dependency.getSourcesArtifact();
				File srcJar = solver.getMoxieCache().getArtifact(sources, sources.extension);
				String jarPath;
				String srcPath;
				if ("var".equals(kind)) {
					// relative to MOXIE_HOME
					jarPath = Toolkit.MOXIE_ROOT + "/" + FileUtils.getRelativePath(config.getMoxieRoot(), jar);
					srcPath = Toolkit.MOXIE_ROOT + "/" + FileUtils.getRelativePath(config.getMoxieRoot(), srcJar);
				} else {
					// filesystem path
					if (extRelative) {
						// relative to project dependency folder
						jar = config.getProjectConfig().getProjectDependencyArtifact(dependency);

						// relative to project dependency source folder
						srcJar = config.getProjectConfig().getProjectDependencySourceArtifact(dependency);

						jarPath = FileUtils.getRelativePath(projectFolder, jar);
						srcPath = FileUtils.getRelativePath(projectFolder, srcJar);
					} else {
						// absolute, hard-coded path to Moxie root
						jarPath = jar.getAbsolutePath();
						srcPath = srcJar.getAbsolutePath();
					}
				}
				if (!jar.exists()) {
					console.error("Excluding {0} from Eclipse classpath because artifact does not exist!", dependency.getCoordinates());
					continue;
				}
				if (srcJar.exists() && srcJar.length() > 1024) {
					// has non-placeholder sources jar
					sb.append(format("<classpathentry kind=\"{0}\" path=\"{1}\" sourcepath=\"{2}\" />\n", kind, jarPath, srcPath));
				} else {
					// no sources
					sb.append(format("<classpathentry kind=\"{0}\" path=\"{1}\" />\n", kind, jarPath));
				}
			}
		}

		for (Build linkedProject : solver.getLinkedModules()) {
			String projectName = null;
			File dotProject = new File(linkedProject.config.getProjectDirectory(), ".project");
			if (dotProject.exists()) {
				// extract Eclipse project name
				console.debug("extracting project name from {0}", dotProject.getAbsolutePath());
				Pattern p = Pattern.compile("(<name>)(.+)(</name>)");
				try {
					Scanner scanner = new Scanner(dotProject);
					while (scanner.hasNextLine()) {
						scanner.nextLine();
						projectName = scanner.findInLine(p);
						if (!StringUtils.isEmpty(projectName)) {
							Matcher m = p.matcher(projectName);
							m.find();
							projectName = m.group(2).trim();
							console.debug(1, projectName);
							break;
						}
					}
					scanner.close();
				} catch (FileNotFoundException e) {
				}
			} else {
				// use folder name
				projectName = linkedProject.config.getProjectDirectory().getName();
			}
			sb.append(format("<classpathentry kind=\"src\" path=\"/{0}\" />\n", projectName));
		}
		sb.append("<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER\" />\n");

		if (settings.groovy) {
			sb.append("<classpathentry exported=\"true\" kind=\"con\" path=\"GROOVY_SUPPORT\" />\n");
			sb.append("<classpathentry exported=\"true\" kind=\"con\" path=\"GROOVY_DSL_SUPPORT\" />\n");
		}
		if (settings.wst){
			sb.append("<classpathentry kind=\"con\" path=\"org.eclipse.jst.j2ee.internal.web.container\" />\n");
			sb.append("<classpathentry kind=\"con\" path=\"org.eclipse.jst.j2ee.internal.module.container\" />\n");
		}

		// determine if we should append an default apt source folder to the classpath
		File aptPrefs = new File(projectFolder, ".settings/org.eclipse.jdt.apt.core.prefs");
		Properties aptProps = readEclipsePrefs(aptPrefs);
		if (Boolean.valueOf(aptProps.getProperty("org.eclipse.jdt.apt.aptEnabled"))) {
			genSrcDir = aptProps.getProperty("org.eclipse.jdt.apt.genSrcDir");
			if (genSrcDir == null) {
				genSrcDir = ".apt_generated";
			}
			sb.append(format("<classpathentry kind=\"src\" path=\"{0}\">\n", genSrcDir));
			sb.append("\t<attributes>\n");
			sb.append("\t\t<attribute name=\"optional\" value=\"true\"/>\n");
			sb.append("\t</attributes>\n");
			sb.append("</classpathentry>\n");
		}

		sb.append(format("<classpathentry kind=\"output\" path=\"{0}\" />\n", FileUtils.getRelativePath(projectFolder, getIDEOutputFolder(Scope.compile))));

		StringBuilder file = new StringBuilder();
		file.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		file.append("<classpath>\n");
		file.append(StringUtils.insertHardTab(sb.toString()));
		file.append("</classpath>\n");

		FileUtils.writeContent(new File(projectFolder, ".classpath"), file.toString());
	}

	private void writeEclipseFactorypath(EclipseSettings settings) {
		File projectFolder = config.getProjectDirectory();
		StringBuilder sb = new StringBuilder();

		// identify apt-processing dependencies on compile classpath
		Set<Dependency> aptDeps = new LinkedHashSet<Dependency>();
		List<Dependency> dependencies = config.getPom().getDependencies(Scope.compile);
		for (Dependency dep : dependencies) {
			if (dep.ring == Constants.RING1 && dep.apt) {
				aptDeps.addAll(solver.getRuntimeDependencies(dep));
			}
		}

		if (aptDeps.size() > 0) {
			// resolve apt artifacts
			Set<File> aptFiles = new LinkedHashSet<File>();
			for (Dependency dep : aptDeps) {
				File file = solver.getArtifact(dep);
				aptFiles.add(file);
			}

			// create the factorypath file
			boolean runInBatchMode = false;
			String pattern = "<factorypathentry kind=\"{0}\" id=\"{1}\" enabled=\"true\" runInBatchMode=\"{2}\" />\n";
			sb.append(MessageFormat.format(pattern, "PLUGIN", "org.eclipse.jst.ws.annotations.core", runInBatchMode));
			// kind choices are EXTJAR, VARJAR, or WKSPJAR
			String kind = settings.var ? "VARJAR" : "EXTJAR";
			for (File file : aptFiles) {
				// default to EXTJAR
				String jarPath = file.getAbsolutePath();
				if ("VARJAR".equals(kind)) {
					// relative to MOXIE_HOME
					jarPath = Toolkit.MOXIE_ROOT + "/" + FileUtils.getRelativePath(config.getMoxieRoot(), file);
				} else if ("WKSPJAR".equals(kind)) {
					// relative to workspace
					// TODO
				}
				sb.append(MessageFormat.format(pattern, kind, jarPath, runInBatchMode));
			}

			// write the factorypath file
			StringBuilder file = new StringBuilder();
			file.append("<factorypath>\n");
			file.append(StringUtils.insertHardTab(sb.toString()));
			file.append("</factorypath>\n");

			FileUtils.writeContent(new File(projectFolder, ".factorypath"), file.toString());

			String genSrcDir = ".apt_generated";
			for (SourceDirectory dir : config.getSourceDirectories()) {
				if (dir.apt && Scope.compile.equals(dir.scope)) {
					genSrcDir = dir.name;
					break;
				}
			}

			// create/update Eclipse apt preferences
			File aptPrefs = new File(projectFolder, ".settings/org.eclipse.jdt.apt.core.prefs");
			Properties aptDefaults = new Properties();
			aptDefaults.put("org.eclipse.jdt.apt.reconcileEnabled", "true");
			Properties aptOverrides = new Properties();
			aptOverrides.put("org.eclipse.jdt.apt.genSrcDir", genSrcDir);
			aptOverrides.put("org.eclipse.jdt.apt.aptEnabled", "true");
			writeEclipsePrefs(aptPrefs, aptDefaults, aptOverrides);

			// create/update Eclipse jdt preferences
			File jdtPrefs = new File(projectFolder, ".settings/org.eclipse.jdt.core.prefs");
			Properties jdtOverrides = new Properties();
			jdtOverrides.put("org.eclipse.jdt.core.compiler.processAnnotations", "enabled");
			writeEclipsePrefs(jdtPrefs, new Properties(), jdtOverrides);
		}
	}

	private Properties readEclipsePrefs(File prefsFile) {
		Properties props = new Properties();
		if (prefsFile.exists()) {
			// load existing prefs file
			FileInputStream is = null;
			try {
				is = new FileInputStream(prefsFile);
				props.load(is);
			} catch (Exception e) {
				getConsole().error(e);
			} finally {
				try {
					is.close();
				} catch (Exception e) {
				}
			}
		}
		return props;
	}

	private void writeEclipsePrefs(File prefsFile, Properties defaults, Properties overrides) {
		// create/update Eclipse preferences
		if (prefsFile.exists()) {
			// load existing prefs file
			Properties props = readEclipsePrefs(prefsFile);
			defaults.putAll(props);
		} else {
			// create .settings folder
			prefsFile.getParentFile().mkdirs();

			// insert prefs version
			defaults.put("eclipse.preferences.version", "1");
		}

		// ensure the overrides are set
		defaults.putAll(overrides);

		FileOutputStream os = null;
		try {
			os = new FileOutputStream(prefsFile);
			defaults.store(os, null);
		} catch (Exception e) {
			getConsole().error(e);
		} finally {
			try {
				os.close();
			} catch (Exception e) {
			}
		}
	}

	private void writeEclipseProject(EclipseSettings settings) {
    	if (!config.getModules().isEmpty()) {
    		// do not write project file for a parent descriptor
    		return;
    	}
		File dotProject = new File(config.getProjectDirectory(), ".project");
		if (dotProject.exists()) {
			// update name and description
			try {
				StringBuilder sb = new StringBuilder();
				Pattern namePattern = Pattern.compile("\\s*?<name>(.+)</name>");
				Pattern descriptionPattern = Pattern.compile("\\s*?<comment>(.+)</comment>");

				boolean replacedName = false;
				boolean replacedDescription = false;

				Scanner scanner = new Scanner(dotProject);
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();

					// replace name
					if (!replacedName) {
						Matcher m = namePattern.matcher(line);
						if (m.matches()) {
							int start = m.start(1);
							int end = m.end(1);
							//console.error("s=" + start + " e=" + end + " l=" + line);
							line = line.substring(0,  start)
									+ config.getPom().getName() + line.substring(end);
							replacedName = true;
						}
					}

					// replace description
					if (!replacedDescription) {
						Matcher m = descriptionPattern.matcher(line);
						if (m.matches()) {
							int start = m.start(1);
							int end = m.end(1);
							//console.error("s=" + start + " e=" + end + " l=" + line);
							line = line.substring(0,  start)
									+ (config.getPom().getDescription() == null ? "" : config.getPom().getDescription())
									+ line.substring(end);
							replacedDescription = true;
						}
					}

					sb.append(line).append('\n');
				}
				scanner.close();

				FileUtils.writeContent(dotProject, sb.toString());
			} catch (FileNotFoundException e) {
			}
			return;
		}

		// create file
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<projectDescription>\n");
		sb.append(MessageFormat.format("\t<name>{0}</name>\n", getPom().name));
		sb.append(MessageFormat.format("\t<comment>{0}</comment>\n", getPom().description == null ? "" : getPom().description));
		sb.append("\t<projects>\n");
		sb.append("\t</projects>\n");
		sb.append("\t<buildSpec>\n");

		List<String> buildCommands = new ArrayList<String>();
		if (config.getSourceDirectories().size() > 0) {
			buildCommands.add("org.eclipse.jdt.core.javabuilder");
		}

		if (settings.wst) {
			buildCommands.add("org.eclipse.wst.common.project.facet.core.builder");
			buildCommands.add("org.eclipse.wst.validation.validationbuilder");
			buildCommands.add("org.eclipse.wst.jsdt.core.javascriptValidator");
		}

		for (String cmd : buildCommands) {
			sb.append("\t\t<buildCommand>\n");
			sb.append(MessageFormat.format("\t\t\t<name>{0}</name>\n", cmd));
			sb.append("\t\t\t<arguments>\n");
			sb.append("\t\t\t</arguments>\n");
			sb.append("\t\t</buildCommand>\n");
		}

		sb.append("\t</buildSpec>\n");
		sb.append("\t<natures>\n");
		if (config.getSourceDirectories().size() > 0) {
			List<String> natures = new ArrayList<String>();
			natures.add("org.eclipse.jdt.core.javanature");
			if (settings.groovy) {
				natures.add("org.eclipse.jdt.groovy.core.groovyNature");
			}
			if (settings.wst){
				natures.add("org.eclipse.wst.common.modulecore.ModuleCoreNature");
				natures.add("org.eclipse.wst.common.project.facet.core.nature");
				natures.add("org.eclipse.wst.jsdt.core.jsNature");
			}
			for (String nature : natures) {
				sb.append(MessageFormat.format("\t\t<nature>{0}</nature>\n", nature));
			}
		}
		sb.append("\t</natures>\n");
		sb.append("</projectDescription>\n\n");

		FileUtils.writeContent(dotProject, sb.toString());
	}

	private void writeIntelliJProject(IntelliJSettings settings) {
    	if (config.getModules().isEmpty()) {
    		// no modules to write project files
    		return;
    	}

		ToolkitConfig project = config.getProjectConfig();

		File dotIdea = new File(project.baseDirectory, ".idea");
		dotIdea.mkdirs();

		// Group name prefers name attribute, but will use groupId if required
		String groupName = project.pom.getGroupId();
		if (!project.pom.getArtifactId().equals(project.pom.getName())) {
			groupName = project.pom.getName();
		}

		List<Module> modules = new ArrayList<Module>(config.getModules());
		Collections.sort(modules);

        StringBuilder sb = new StringBuilder();
		for (Module module : modules) {
			File moduleFolder = new File(project.baseDirectory, module.folder);
			File configFile = new File(moduleFolder, module.descriptor);
			if (!configFile.exists()) {
				continue;
			}
			ToolkitConfig moduleConfig;
			try {
				moduleConfig = new ToolkitConfig(configFile, moduleFolder, Toolkit.MOXIE_DEFAULTS);
				if (StringUtils.isEmpty(moduleConfig.getPom().artifactId)) {
					console.warn(2, "excluding module ''{0}'' from IntelliJ IDEA project because it has no artifactId!", module.folder);
					continue;
				}
				if (moduleConfig.getPom().isPOM()) {
					// skip pom modules
					console.warn(2, "excluding module ''{0}'' from IntelliJ IDEA project because it is a POM module!", module.folder);
					continue;
				}
				if (moduleConfig.getSourceDirectories().isEmpty()) {
					// skip modules without source folders
					console.warn(2, "excluding module ''{0}'' from IntelliJ IDEA project because it has no source directories!", module.folder);
					continue;
				}
				sb.append(format("<module fileurl=\"file://$PROJECT_DIR$/{0}/{1}.iml\" filepath=\"$PROJECT_DIR$/{0}/{1}.iml\" group=\"{2}\" />\n",
						module.folder, moduleConfig.getPom().artifactId, groupName));
			} catch (Exception e) {
				console.error(e, "Failed to parse {0} for module {1}!", module.descriptor, module.folder);
			}
		}

        StringBuilder modulesStr = new StringBuilder();
        modulesStr.append("<modules>\n");
        modulesStr.append(StringUtils.insertHalfTab(sb.toString()));
        modulesStr.append("</modules>");

        StringBuilder component = new StringBuilder();
        component.append("<component name=\"ProjectModuleManager\">\n");
        component.append(StringUtils.insertHalfTab(modulesStr.toString()));
        component.append("</component>");

        StringBuilder file = new StringBuilder();
        file.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        file.append("<project version=\"4\">\n");
        file.append(StringUtils.insertHalfTab(component.toString()));
        file.append("</project>\n\n");
        FileUtils.writeContent(new File(dotIdea, "modules.xml"), file.toString());
	}

	private void writeIntelliJAnt() {
    	if (config.getModules().isEmpty()) {
    		// no modules to write project files
    		return;
    	}

		ToolkitConfig project = config.getProjectConfig();

		File dotIdea = new File(project.baseDirectory, ".idea");
		dotIdea.mkdirs();
    	File antFile = new File(dotIdea, "ant.xml");
    	if (antFile.exists()) {
    		// do not attempt to update this file
    		return;
    	}

        StringBuilder sb = new StringBuilder();

        File rootAnt = new File(project.baseDirectory, "build.xml");
        if (rootAnt.exists()) {
        	sb.append(format("<buildFile url=\"file://$PROJECT_DIR$/{0}\" />\n", rootAnt.getName()));
        }

		for (Module module : project.modules) {
			File moduleFolder = new File(project.baseDirectory, module.folder);
			File scriptFile = new File(moduleFolder, module.script);
			if (!scriptFile.exists()) {
				continue;
			}
			sb.append(format("<buildFile url=\"file://$PROJECT_DIR$/{0}/{1}\" />\n", module.folder, module.script));
		}

        StringBuilder component = new StringBuilder();
        component.append("<component name=\"AntConfiguration\">\n");
        component.append(StringUtils.insertHalfTab(sb.toString()));
        component.append("</component>");

        StringBuilder file = new StringBuilder();
        file.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        file.append("<project version=\"4\">\n");
        file.append(StringUtils.insertHalfTab(component.toString()));
        file.append("</project>\n\n");
        FileUtils.writeContent(antFile, file.toString());
	}

    private void writeIntelliJClasspath(IntelliJSettings settings) {
    	if (config.getSourceDirectories().isEmpty()
    			|| config.getPom().isPOM()
    			|| !config.getModules().isEmpty()) {
    		// no classpath to write
    		return;
    	}

        File projectFolder = config.getProjectDirectory();

        StringBuilder sb = new StringBuilder();
        sb.append(format("<output url=\"file://$MODULE_DIR$/{0}\" />\n", FileUtils.getRelativePath(projectFolder, getIDEOutputFolder(Scope.compile))));
        sb.append(format("<output-test url=\"file://$MODULE_DIR$/{0}\" />\n", FileUtils.getRelativePath(projectFolder, getIDEOutputFolder(Scope.test))));
        sb.append("<exclude-output />\n");
        sb.append("<content url=\"file://$MODULE_DIR$\">\n");
		List<SourceDirectory> sourceDirs = new ArrayList<SourceDirectory>();
		sourceDirs.addAll(config.getProjectConfig().getSourceDirectories());
		sourceDirs.addAll(config.getProjectConfig().getResourceDirectories());
        StringBuilder sf = new StringBuilder();
        for (SourceDirectory sourceFolder : sourceDirs) {
            if (Scope.site.equals(sourceFolder.scope)) {
                continue;
            }
            sf.append(format("<sourceFolder url=\"file://$MODULE_DIR$/{0}\" isTestSource=\"{1}\" />\n", FileUtils.getRelativePath(projectFolder, sourceFolder.getSources()), Scope.test.equals(sourceFolder.scope)));
        }
        sb.append(StringUtils.insertHalfTab(sf.toString()));
        sb.append("</content>\n");
        sb.append("<orderEntry type=\"sourceFolder\" forTests=\"false\" />\n");

        // determine how to output dependencies (fixed-path or variable-relative)
        boolean variableRelative = false;
        boolean extRelative = getConfig().getProjectConfig().dependencyDirectory != null && getConfig().getProjectConfig().dependencyDirectory.exists();

        // always link classpath against Moxie artifact cache
        Set<Dependency> dependencies = new LinkedHashSet<Dependency>();
        dependencies.addAll(solver.solve(Scope.compile));
        // add unique test classpath items
        dependencies.addAll(solver.solve(Scope.test));

        for (Dependency dependency : dependencies) {
            Scope scope = null;
            File jar = null;
            File srcJar = null;
            String jarPath = null;
            String srcPath = null;

            if (dependency instanceof SystemDependency) {
                SystemDependency sys = (SystemDependency) dependency;
                jar = new File(sys.path);
                jarPath = format("jar://{0}!/", jar.getAbsolutePath());
            } else {
            	if (dependency.definedScope == null) {
            		getConsole().error("{0} is missing a definedScope!", dependency.getCoordinates());
            	}
                // COMPILE scope is always implied and unnecessary in iml file
                if (!dependency.definedScope.isDefault()) {
                    scope = dependency.definedScope;
                }

                jar = solver.getMoxieCache().getArtifact(dependency, dependency.extension);
                Dependency sources = dependency.getSourcesArtifact();
                srcJar = solver.getMoxieCache().getArtifact(sources, sources.extension);

                if (variableRelative) {
                    // relative to MOXIE_HOME
                    jarPath = format("jar://$" + Toolkit.MOXIE_ROOT + "$/{0}!/", FileUtils.getRelativePath(config.getMoxieRoot(), jar));
                    srcPath = format("jar://$" + Toolkit.MOXIE_ROOT + "$/{0}!/", FileUtils.getRelativePath(config.getMoxieRoot(), srcJar));
                } else {
                    // filesystem path
                    if (extRelative) {
						// relative to project dependency folder
						jar = config.getProjectConfig().getProjectDependencyArtifact(dependency);

						// relative to project dependency source folder
						srcJar = config.getProjectConfig().getProjectDependencySourceArtifact(dependency);

                        jarPath = format("jar://$MODULE_DIR$/{0}!/", FileUtils.getRelativePath(projectFolder, jar));
                        srcPath = format("jar://$MODULE_DIR$/{0}!/", FileUtils.getRelativePath(projectFolder, srcJar));
                    } else {
                        // relative to USER_HOME
                        jarPath = format("jar://$USER_HOME$/.moxie/{0}!/", FileUtils.getRelativePath(config.getMoxieRoot(), jar));
                        srcPath = format("jar://$USER_HOME$/.moxie/{0}!/", FileUtils.getRelativePath(config.getMoxieRoot(), srcJar));
                    }
                }
            }

			if (!jar.exists()) {
				console.error("Excluding {0} from IntelliJ IDEA classpath because artifact does not exist!", dependency.getCoordinates());
				continue;
			}

            if (scope == null) {
                sb.append("<orderEntry type=\"module-library\">\n");
            } else {
                sb.append(format("<orderEntry type=\"module-library\" scope=\"{0}\">\n", scope.name().toUpperCase()));
            }
            StringBuilder lib = new StringBuilder();
            lib.append(format("<library name=\"{0}\">\n", jar.getName()));
            StringBuilder CLASSES = new StringBuilder();
            CLASSES.append("<CLASSES>\n");
            CLASSES.append(StringUtils.insertHalfTab(format("<root url=\"{0}\" />\n", jarPath)));
            CLASSES.append("</CLASSES>\n");
            lib.append(StringUtils.insertHalfTab(CLASSES.toString()));
            lib.append(StringUtils.insertHalfTab("<JAVADOC />\n"));
            if (srcJar != null && srcJar.exists() && srcJar.length() > 1024) {
                StringBuilder SOURCES = new StringBuilder();
                SOURCES.append("<SOURCES>\n");
                SOURCES.append(StringUtils.insertHalfTab(format("<root url=\"{0}\" />\n", srcPath)));
                SOURCES.append("</SOURCES>\n");
                lib.append(StringUtils.insertHalfTab(SOURCES.toString()));
            } else {
                lib.append(StringUtils.insertHalfTab("<SOURCES />\n"));
            }
            lib.append("</library>\n");
            sb.append(StringUtils.insertHalfTab(lib.toString()));
            sb.append("</orderEntry>\n");
        }

        for (Build linkedProject : solver.getLinkedModules()) {
            String artifactId = linkedProject.getPom().getArtifactId();
            sb.append(format("<orderEntry type=\"module\" module-name=\"{0}\" />\n", artifactId));
        }
        sb.append("<orderEntry type=\"inheritedJdk\" />\n");

        StringBuilder component = new StringBuilder();
        component.append("<component name=\"NewModuleRootManager\" inherit-compiler-output=\"false\">\n");
        component.append(StringUtils.insertHalfTab(sb.toString()));
        component.append("</component>");

        StringBuilder file = new StringBuilder();
        file.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        file.append("<module type=\"JAVA_MODULE\" version=\"4\">\n");
        file.append(StringUtils.insertHalfTab(component.toString()));
        file.append("</module>\n\n");

        String name = config.getPom().getArtifactId();
        FileUtils.writeContent(new File(projectFolder, name + ".iml"), file.toString());
    }

	private void writePOM() {
		if (config.getSourceDirectories().isEmpty()
    			|| config.getPom().isPOM()
    			|| !config.getModules().isEmpty()) {
    		// no POM to write
    		return;
    	}
		StringBuilder sb = new StringBuilder();
		sb.append("<!-- This file is automatically generated by Moxie. DO NOT HAND EDIT! -->\n");
		sb.append(getPom().toXML(false, config.getRepositoryDefinitions()));
		FileUtils.writeContent(new File(config.getProjectDirectory(), "pom.xml"), sb.toString());
	}

	public void describe() {
		console.title(getPom().name, getPom().version);

		describeConfig();
		describeSettings();
	}

	void describeConfig() {
		Pom pom = getPom();
		console.log("project metadata");
		describe(Key.name, pom.name);
		describe(Key.description, pom.description);
		describe(Key.groupId, pom.groupId);
		describe(Key.artifactId, pom.artifactId);
		describe(Key.version, pom.version);
		describe(Key.organization, pom.organization);
		describe(Key.url, pom.url);

		if (!solver.isOnline()) {
			console.separator();
			console.warn("Moxie is running offline. Network functions disabled.");
		}

		if (config.isVerbose()) {
			console.separator();
			console.log("source directories");
			for (SourceDirectory directory : config.getSourceDirectories()) {
				console.sourceDirectory(directory);
			}
			console.separator();
			console.log("resource directories");
			for (SourceDirectory directory : config.getResourceDirectories()) {
				console.sourceDirectory(directory);
			}
			console.separator();

			console.log("output directory");
			console.log(1, config.getOutputDirectory(null).toString());
			console.separator();
		}
	}

	void describeSettings() {
		if (config.isVerbose()) {
			console.log("Moxie parameters");
			describe(Toolkit.MX_ROOT, solver.getMoxieCache().getRootFolder().getAbsolutePath());
			describe(Toolkit.MX_ONLINE, "" + solver.isOnline());
			describe(Toolkit.MX_UPDATEMETADATA, "" + solver.isUpdateMetadata());
			describe(Toolkit.MX_DEBUG, "" + config.isDebug());
			describe(Toolkit.MX_VERBOSE, "" + config.isVerbose());
			describe(Toolkit.Key.mavenCacheStrategy, config.getMavenCacheStrategy().name());

			console.log("dependency sources");
			if (config.getRepositories().size() == 0) {
				console.error("no dependency sources defined!");
			}
			for (Repository repository : config.getRepositories()) {
				console.log(1, repository.toString());
				console.download(repository.getRepositoryUrl());
				console.log();
			}

			List<Proxy> actives = config.getMoxieConfig().getActiveProxies();
			if (actives.size() > 0) {
				console.log("proxy settings");
				for (Proxy proxy : actives) {
					if (proxy.active) {
						describe("proxy", proxy.host + ":" + proxy.port);
					}
				}
				console.separator();
			}
		}
	}

	void describe(Enum<?> key, String value) {
		describe(key.name(), value);
	}

	void describe(String key, String value) {
		if (StringUtils.isEmpty(value)) {
			return;
		}
		console.key(StringUtils.leftPad(key, 12, ' '), value);
	}

	@Override
	public String toString() {
		return "Build (" + getPom().toString() + ")";
	}
}
