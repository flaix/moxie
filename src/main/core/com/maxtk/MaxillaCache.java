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
package com.maxtk;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.maxtk.utils.FileUtils;

public class MaxillaCache extends ArtifactCache {

	final ArtifactCache mavenCache;
	final File dataRoot;
	
	public MaxillaCache() {
		this(new File(System.getProperty("user.home") + "/.maxilla"), new File(System.getProperty("user.home") + "/.m2/repository"));
	}
	
	public MaxillaCache(File maxillaRoot, File mavenRoot) {
		super(new File(maxillaRoot, "repository"));
		mavenCache = new ArtifactCache(mavenRoot);
		dataRoot = new File(maxillaRoot, "data");
	}
	
	@Override
	public File getFile(Dependency dep, String ext) {
		String path = Dependency.getMaxillaPath(dep, ext, pattern);
	
		File maxillaFile = new File(root, path);
		File mavenFile = mavenCache.getFile(dep, ext);
		
		if (!maxillaFile.exists() && mavenFile.exists()) {
			// transparently copy from Maven cache to Maxilla cache
			try {
				FileUtils.copy(maxillaFile.getParentFile(), mavenFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return maxillaFile;
	}
	
	@Override
	public File getSolution(Dependency dep) {
		if (!dep.isMavenObject()) {
			return null;
		}

		String path = Dependency.getMaxillaPath(dep, "maxml", pattern);
		File maxillaFile = new File(dataRoot, path);
		return maxillaFile;
	}
}