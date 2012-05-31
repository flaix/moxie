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
package com.maxtk.ant;

import java.io.File;

import com.maxtk.Build;
import com.maxtk.Dependency;

public class MxGitTask extends MxTask {

	protected File repositoryFolder;

	public void setRepositoryFolder(String path) {
		this.repositoryFolder = new File(path);
	}

	protected void loadDependency(Build build) {
		build.loadDependency(new Dependency("org.eclipse.jgit:org.eclipse.jgit:1.3.0.201202151440-r"));
	}
}