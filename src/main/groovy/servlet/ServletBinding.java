/*
 $Id$

 Copyright 2005 (C) Guillaume Laforge. All Rights Reserved.

 Redistribution and use of this software and associated documentation
 ("Software"), with or without modification, are permitted provided
 that the following conditions are met:

 1. Redistributions of source code must retain copyright
    statements and notices.  Redistributions must also contain a
    copy of this document.

 2. Redistributions in binary form must reproduce the
    above copyright notice, this list of conditions and the
    following disclaimer in the documentation and/or other
    materials provided with the distribution.

 3. The name "groovy" must not be used to endorse or promote
    products derived from this Software without prior written
    permission of The Codehaus.  For written permission,
    please contact info@codehaus.org.

 4. Products derived from this Software may not be called "groovy"
    nor may "groovy" appear in their names without prior written
    permission of The Codehaus. "groovy" is a registered
    trademark of The Codehaus.

 5. Due credit should be given to The Codehaus -
    http://groovy.codehaus.org/

 THIS SOFTWARE IS PROVIDED BY THE CODEHAUS AND CONTRIBUTORS
 ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT
 NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL
 THE CODEHAUS OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 OF THE POSSIBILITY OF SUCH DAMAGE.

 */
package groovy.servlet;

import groovy.lang.Binding;
import groovy.xml.MarkupBuilder;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet-specific binding extension to lazy load the writer or the output
 * stream from the response.
 * 
 * <p>
 * <h3>Default variables bound</h3>
 * <ul>
 * <li><tt>"request"</tt> : the HttpServletRequest object</li>
 * <li><tt>"response"</tt> : the HttpServletResponse object</li>
 * <li><tt>"context"</tt> : the ServletContext object </li>
 * <li><tt>"application"</tt> : same as context</li>
 * <li><tt>"session"</tt> : convenient for <code>request.getSession(<b>false</b>)</code> - can be null!</li>
 * <li><tt>"params"</tt> : map of all form parameters - can be empty</li>
 * <li><tt>"headers"</tt> : map of all <b>request</b> header fields</li>
 * </ul>
 * 
 * <p>
 * <h3>Implicite bound variables</h3>
 * <ul>
 * <li><tt>"out"</tt> : response.getWriter() </li>
 * <li><tt>"sout"</tt> : response.getOutputStream() </li>
 * <li><tt>"html"</tt> : new MarkupBuilder(response.getWriter()) </li>
 * </ul>
 * </p>
 * 
 * @author Guillaume Laforge
 * @author Christian Stein
 */
public class ServletBinding extends Binding {

    private final Binding binding;

    private final ServletContext context;

    private final HttpServletRequest request;

    private final HttpServletResponse response;

    private MarkupBuilder html;

    /**
     * Initializes a servlet binding.
     */
    public ServletBinding(HttpServletRequest request, HttpServletResponse response, ServletContext context) {
        this.binding = new Binding();
        this.request = request;
        this.response = response;
        this.context = context;

        /*
         * Bind the default variables.
         */
        binding.setVariable("request", request);
        binding.setVariable("response", response);
        binding.setVariable("context", context);
        binding.setVariable("application", context);

        /*
         * Bind the HTTP session object - if there is one.
         * Note: we don't create one here!
         */
        binding.setVariable("session", request.getSession(false));

        /*
         * Bind form parameter key-value hash map.
         *
         * If there are multiple, they are passed as an array.
         */
        Map params = new HashMap();
        for (Enumeration names = request.getParameterNames(); names.hasMoreElements();) {
            String name = (String) names.nextElement();
            if (!binding.getVariables().containsKey(name)) {
                String[] values = request.getParameterValues(name);
                if (values.length == 1) {
                    params.put(name, values[0]);
                } else {
                    params.put(name, values);
                }
            }
        }
        binding.setVariable("params", params);

        /*
         * Bind request header key-value hash map.
         */
        Map headers = new HashMap();
        for (Enumeration names = request.getHeaderNames(); names.hasMoreElements();) {
            String headerName = (String) names.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName, headerValue);
        }
        binding.setVariable("headers", headers);
    }

    public void setVariable(String name, Object value) {
        /*
         * Check sanity.
         */
        if (name == null) {
            throw new IllegalArgumentException("Can't bind variable to null key.");
        }
        if (name.length() == 0) {
            throw new IllegalArgumentException("Can't bind variable to blank key name. [length=0]");
        }
        /*
         * Check implicite key names. See getVariable(String)!
         */
        if ("out".equals(name)) {
            throw new IllegalArgumentException("Can't bind variable to key named '" + name + "'.");
        }
        if ("sout".equals(name)) {
            throw new IllegalArgumentException("Can't bind variable to key named '" + name + "'.");
        }
        if ("html".equals(name)) {
            throw new IllegalArgumentException("Can't bind variable to key named '" + name + "'.");
        }
        /*
         * TODO Check default key names. See constructor(s).
         */
        
        /*
         * All checks passed, set the variable.
         */
        binding.setVariable(name, value);
    }

    public Map getVariables() {
        return binding.getVariables();
    }

    /**
     * @return a writer, an output stream, a markup builder or another requested object
     */
    public Object getVariable(String name) {
        /*
         * Check sanity.
         */
        if (name == null) {
            throw new IllegalArgumentException("Can't bind variable to null key.");
        }
        if (name.length() == 0) {
            throw new IllegalArgumentException("Can't bind variable to blank key name. [length=0]");
        }
        /*
         * Check implicite key names. See setVariable(String, Object)!
         */
        try {
            if ("out".equals(name)) {
                return response.getWriter();
            }
            if ("sout".equals(name)) {
                return response.getOutputStream();
            }
            if ("html".equals(name)) {
                if (html == null) {
                    html = new MarkupBuilder(response.getWriter());
                }
                return html;
            }
        } catch (IOException e) {
            String message = "Failed to get writer or output stream from response.";
            context.log(message, e);
            throw new RuntimeException(message, e);
        }
        /*
         * Still here? Delegate to the binding object.
         */
        return binding.getVariable(name);
    }
}
