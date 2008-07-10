/*
 * Copyright 2008 the original author or authors.
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
package org.codehaus.groovy.reflection.utils;

import org.codehaus.groovy.reflection.ReflectionUtils;

/**
 * Created by IntelliJ IDEA.
 * User: Danno.Ferrin
 * Date: Jun 24, 2008
 * Time: 9:50:39 PM
 */
class PojoCalleTestClass {
    public static Class staticClassCaller() {
        return ReflectionUtils.getCallingClass();
    }

    public Class instanceCaller() {
        return ReflectionUtils.getCallingClass();
    }
}