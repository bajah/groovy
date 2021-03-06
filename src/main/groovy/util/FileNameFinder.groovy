/*
 * Copyright 2003-2007 the original author or authors.
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

package groovy.util

/**
 * Find files according to a base directory and an includes and excludes pattern.
 * The include and exclude patterns conform to Ant's fileset pattern conventions.
 *
 *   @author Dierk Koenig
 *   @author Paul King
 */
class FileNameFinder implements IFileNameFinder {

    List getFileNames(String basedir, String pattern) {
        return getFileNames(dir: basedir, includes: pattern)
    }

    List getFileNames(String basedir, String pattern, String excludesPattern) {
        return getFileNames(dir: basedir, includes: pattern, excludes: excludesPattern)
    }

    List getFileNames(Map args) {
        def ant = new AntBuilder()
        def scanner = ant.fileScanner {
            fileset(args)
        }
        def fls = []
        for (f in scanner) {
            fls << f.getAbsolutePath()
        }
        return fls
    }
}