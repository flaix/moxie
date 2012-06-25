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

public class Constants {

	public static final String HDR = "=========================================================";

	public static final String SUB = "---------------------------------------------------------";
	
	public static final String SEP = "---------------------------------------------------------";

	public static final String INDENT = "   ";
	
	public static final String MAVEN2_PATTERN = "${groupId}/${artifactId}/${version}/${artifactId}-${revision}${classifier}.${ext}";

	public static final String MAVEN2_METADATA_PATTERN = "${groupId}/${artifactId}/maven-metadata.${ext}";
	
	public static final String MAVEN2_SNAPSHOT_PATTERN = "${groupId}/${artifactId}/${version}/maven-metadata.${ext}";
	
	public static final String APPLY_ECLIPSE = "eclipse";
	
	public static final String APPLY_POM = "pom";
	
	public static final String APPLY_COLOR = "color";
	
	public static final String APPLY_DEBUG = "debug";
	
	public static final String APPLY_CACHE = "cache";
	
	public static final String DEFAULT_EXCLUDES = "**/*.java, **/package.html, **/Thumbs.db, **/.svn, **/CVS, **/.gitignore, **/.hgignore, **/.hgtags";
	
	public static final String RELEASE = "RELEASE";
	
	public static final String LATEST = "LATEST";
	
	public static final String POM = "pom";
	
	public static final String XML = "xml";
	
	public static final String MX_DEBUG = "mx.debug";

	public static final String MX_VERBOSE = "mx.verbose";
	
	public static final String MX_COLOR = "mx.color";
	
	public static final String MX_ONLINE = "mx.online";
	
	public static final String MOXIE_SETTINGS = "settings.moxie";
	
	public static final String MOXIE_DEFAULTS = "defaults.moxie";
	
	public static enum Key {
		build, name, description, url, organization, scope, groupId, artifactId, version,
		type, classifier, optional, folder, sourceFolder, sourceFolders, compile_sourcepath,
		test_sourcepath, outputFolder, compile_outputFolder, test_outputFolder, linkedProjects,
		dependencyFolder, repositories, properties, dependencies, apply,
		googleAnalyticsId, googlePlusId, runtime_classpath, compile_classpath, test_classpath,
		compile_dependencypath, runtime_dependencypath, test_dependencypath, commit, targetFolder,
		proxies, parent, exclusions, mxjar, mxjavac, compilerArgs, excludes, includes,
		dependencyManagement, mxreport, outputFile, verbose, build_classpath, reportsFolder,
		dependencyOverrides, dependencyAliases, updatePolicy, lastChecked, lastUpdated, lastSolved,
		lastDownloaded, origin, release, latest, revision, packaging;

		public String projectId() {
			return "project." + name().replace('_', '.');
		}

		public String propId() {
			return "mxp." + name().replace('_', '.');
		}
		
		public String refId() {
			return "mxr." + name().replace('_', '.');
		}

	}
	
	public static String getVersion() {
		String v = Constants.class.getPackage().getImplementationVersion();
		if (v == null) {
			return "DEVELOPMENT";
		}
		return v;
	}
}
