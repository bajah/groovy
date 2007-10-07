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

package org.codehaus.groovy.tools.shell.expr

import org.codehaus.groovy.tools.shell.Groovysh

/**
 * Tests for <tt>time = { it() }</tt> expressions.
 *
 * @version $Id$
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 */
class TimeItTest
    extends GroovyTestCase
{
    Groovysh shell

    void setUp() {
        super.setUp()

        shell = new Groovysh()

        shell.errorHook = { Throwable cause ->
            throw cause
        }

        shell.resultHook = { result ->
            // ignore
        }
    }

    void testSingleLine() {
        def result = shell.execute('time = { it() }')
        assert result != null
    }

    void testMultiLine() {
        def result

        result = shell.execute('time = {')
        assert result == null

        result = shell.execute('it()')
        assert result == null

        result = shell.execute('}')
        assert result != null
    }
}