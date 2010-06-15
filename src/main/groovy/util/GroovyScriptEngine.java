/*
 * Copyright 2003-2009 the original author or authors.
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
package groovy.util;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * Specific script engine able to reload modified scripts as well as dealing properly with dependent scripts.
 *
 * @author sam
 * @author Marc Palmer
 * @author Guillaume Laforge
 */
public class GroovyScriptEngine implements ResourceConnector {

    /**
     * Simple testing harness for the GSE. Enter script roots as arguments and
     * then input script names to run them.
     *
     * @param urls an array of URLs
     * @throws Exception if something goes wrong
     */
    public static void main(String[] urls) throws Exception {
        GroovyScriptEngine gse = new GroovyScriptEngine(urls);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while (true) {
            System.out.print("groovy> ");
            if ((line = br.readLine()) == null || line.equals("quit"))
                break;
            try {
                System.out.println(gse.run(line, new Binding()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private URL[] roots;
    private Map<String, ScriptCacheEntry> scriptCache = Collections.synchronizedMap(new HashMap<String, ScriptCacheEntry>());
    private ResourceConnector rc;

    private static ThreadLocal<ScriptCacheEntry> currentCacheEntryHolder = new ThreadLocal<ScriptCacheEntry>();
    private GroovyClassLoader groovyLoader = null;

    private static class ScriptCacheEntry {
        private Class scriptClass;
        private long lastModified;
        private Map<URL, Long> dependencies = new HashMap<URL, Long>();
    }

    private class ScriptClassLoader extends GroovyClassLoader {
        public ScriptClassLoader(ClassLoader loader) {
            super(loader);
        }

        public ScriptClassLoader(GroovyClassLoader parent) {
            super(parent);
        }

        protected Class findClass(String className) throws ClassNotFoundException {
            String filename = className.replace('.', File.separatorChar) + ".groovy";
            URLConnection dependentScriptConn = null;
            try {
                dependentScriptConn = rc.getResourceConnection(filename);
                ScriptCacheEntry currentCacheEntry = currentCacheEntryHolder.get();
                if (currentCacheEntry != null) {
                    currentCacheEntry.dependencies.put(dependentScriptConn.getURL(), dependentScriptConn.getLastModified());
                }
                return parseClass(dependentScriptConn.getInputStream(), filename);
            } catch (ResourceException e1) {
                throw new ClassNotFoundException("Could not read " + className + ": " + e1);
            } catch (CompilationFailedException e2) {
                throw new ClassNotFoundException("Syntax error in " + className + ": " + e2);
            } catch (IOException e3) {
                throw new ClassNotFoundException("Problem reading " + className + ": " + e3);
            } finally {
                forceClose(dependentScriptConn);
            }
        }
    }

    /*
     * Initialize a new GroovyClassLoader with the parentClassLoader passed as a parameter.
     *
     * @param parentClassLoader the class loader to use
     */
    private void initGroovyLoader(final ClassLoader parentClassLoader) {
        if (groovyLoader == null || groovyLoader.getParent() != parentClassLoader) {
            groovyLoader = AccessController.doPrivileged(new PrivilegedAction<GroovyClassLoader>() {
                public GroovyClassLoader run() {
                    ScriptClassLoader loader;
                    if (parentClassLoader instanceof GroovyClassLoader)
                        loader = new ScriptClassLoader((GroovyClassLoader) parentClassLoader);
                    else
                        loader = new ScriptClassLoader(parentClassLoader);
                    return loader;
                }
            });
        }
    }

    /**
     * Get a resource connection as a <code>URLConnection</code> to retrieve a script
     * from the <code>ResourceConnector</code>.
     *
     * @param resourceName name of the resource to be retrieved
     * @return a URLConnection to the resource
     * @throws ResourceException
     */
    public URLConnection getResourceConnection(String resourceName) throws ResourceException {
        // Get the URLConnection
        URLConnection groovyScriptConn = null;
        ResourceException se = null;
        for (URL root : roots) {
            URL scriptURL = null;
            try {
                scriptURL = new URL(root, resourceName);
                groovyScriptConn = scriptURL.openConnection();

                // Make sure we can open it, if we can't it doesn't exist.
                // Could be very slow if there are any non-file:// URLs in there
                groovyScriptConn.getInputStream();
                break; // Now this is a bit unusual
            } catch (MalformedURLException e) {
                String message = "Malformed URL: " + root + ", " + resourceName;
                if (se == null) {
                    se = new ResourceException(message);
                } else {
                    se = new ResourceException(message, se);
                }
            } catch (IOException e1) {
                String message = "Cannot open URL: " + scriptURL;
                if (se == null) {
                    se = new ResourceException(message);
                } else {
                    se = new ResourceException(message, se);
                }
            }
        }

        if(se == null) se = new ResourceException("No resource for " + resourceName + " was found");
        
        // If we didn't find anything, report on all the exceptions that occurred.
        if (groovyScriptConn == null) {
            throw se;
        }
        return groovyScriptConn;
    }

    /**
     * The groovy script engine will run groovy scripts and reload them and
     * their dependencies when they are modified. This is useful for embedding
     * groovy in other containers like games and application servers.
     *
     * @param roots This an array of URLs where Groovy scripts will be stored. They should
     *              be layed out using their package structure like Java classes
     */
    public GroovyScriptEngine(URL[] roots) {
        this.roots = roots;
        this.rc = this;
        initGroovyLoader(getClass().getClassLoader());
    }

    public GroovyScriptEngine(URL[] roots, ClassLoader parentClassLoader) {
        this(roots);
        initGroovyLoader(parentClassLoader);
    }

    public GroovyScriptEngine(String[] urls) throws IOException {
        roots = new URL[urls.length];
        for (int i = 0; i < roots.length; i++) {
            if (urls[i].indexOf("://") != -1) {
                roots[i] = new URL(urls[i]);
            } else {
                roots[i] = new File(urls[i]).toURI().toURL();
            }
        }
        this.rc = this;
        initGroovyLoader(getClass().getClassLoader());
    }

    public GroovyScriptEngine(String[] urls, ClassLoader parentClassLoader) throws IOException {
        this(urls);
        initGroovyLoader(parentClassLoader);
    }

    public GroovyScriptEngine(String url) throws IOException {
        this(new String[]{url});
    }

    public GroovyScriptEngine(String url, ClassLoader parentClassLoader) throws IOException {
        this(url);
        initGroovyLoader(parentClassLoader);
    }

    public GroovyScriptEngine(ResourceConnector rc) {
        this.rc = rc;
        initGroovyLoader(getClass().getClassLoader());
    }

    public GroovyScriptEngine(ResourceConnector rc, ClassLoader parentClassLoader) {
        this(rc);
        initGroovyLoader(parentClassLoader);
    }

    /**
     * Get the <code>ClassLoader</code> that will serve as the parent ClassLoader of the
     * {@link GroovyClassLoader} in which scripts will be executed. By default, this is the
     * ClassLoader that loaded the <code>GroovyScriptEngine</code> class.
     *
     * @return parent classloader used to load scripts
     */
    public ClassLoader getParentClassLoader() {
        return groovyLoader.getParent();
    }

    /**
     * @param parentClassLoader ClassLoader to be used as the parent ClassLoader for scripts executed by the engine
     * @deprecated
     */
    public void setParentClassLoader(ClassLoader parentClassLoader) {
        if (parentClassLoader == null) {
            throw new IllegalArgumentException("The parent class loader must not be null.");
        }
        initGroovyLoader(parentClassLoader);
    }

    /**
     * Get the class of the scriptName in question, so that you can instantiate Groovy objects with caching and reloading.
     *
     * @param scriptName resource name pointing to the script
     * @return the loaded scriptName as a compiled class
     * @throws ResourceException if there is a problem accessing the script
     * @throws ScriptException   if there is a problem parsing the script
     */
    public Class loadScriptByName(String scriptName) throws ResourceException, ScriptException {
        scriptName = scriptName.replace('.', File.separatorChar) + ".groovy";
        ScriptCacheEntry entry = updateCacheEntry(scriptName);
        return entry.scriptClass;
    }

    /**
     * Get the class of the scriptName in question, so that you can instantiate Groovy objects with caching and reloading.
     *
     * @param scriptName resource name pointing to the script
     * @param parentClassLoader the class loader to use when loading the script
     * @return the loaded scriptName as a compiled class
     * @throws ResourceException if there is a problem accessing the script
     * @throws ScriptException if there is a problem parsing the script
     * @deprecated
     */
    public Class loadScriptByName(String scriptName, ClassLoader parentClassLoader)
            throws ResourceException, ScriptException {
        initGroovyLoader(parentClassLoader);
        return loadScriptByName(scriptName);
    }

    /**
     * Locate the class and reload it or any of its dependencies
     *
     * @param scriptName resource name pointing to the script
     * @return the cache entry for scriptName
     * @throws ResourceException if there is a problem accessing the script
     * @throws ScriptException   if there is a problem parsing the script
     */
    private ScriptCacheEntry updateCacheEntry(String scriptName) throws ResourceException, ScriptException {
        ScriptCacheEntry entry;

        scriptName = scriptName.intern();
        synchronized (scriptName) {

            URLConnection scriptConn = rc.getResourceConnection(scriptName);

            // URL last modified
            long lastModified = scriptConn.getLastModified();
            // Check the cache for the scriptName
            entry = scriptCache.get(scriptName);

            if (entry == null || entry.lastModified < lastModified || dependencyOutOfDate(entry)) {
                ScriptCacheEntry cacheEntry = new ScriptCacheEntry();
                currentCacheEntryHolder.set(cacheEntry);
                cacheEntry.scriptClass = parseScript(scriptName, scriptConn);
                cacheEntry.lastModified = lastModified;
                scriptCache.put(scriptName, cacheEntry);
                entry = cacheEntry;
                cacheEntry = null;
            } else {
                forceClose(scriptConn);
            }
        }
        return entry;
    }

    private Class parseScript(String scriptName, URLConnection scriptConn) throws ScriptException {
        try {
            InputStream in = scriptConn.getInputStream();
            return groovyLoader.parseClass(in, scriptName);
        } catch (Exception e) {
            throw new ScriptException("Could not parse script: " + scriptName, e);
        } finally {
            currentCacheEntryHolder.set(null);
            forceClose(scriptConn);
        }
    }

    private boolean dependencyOutOfDate(ScriptCacheEntry cacheEntry) {
        if (cacheEntry != null) {
            for (URL url : cacheEntry.dependencies.keySet()) {
                URLConnection urlc = null;
                try {
                    urlc = url.openConnection();
                    urlc.setDoInput(false);
                    urlc.setDoOutput(false);
                    long dependentLastModified = urlc.getLastModified();
                    if (dependentLastModified > cacheEntry.dependencies.get(url)) {
                        return true;
                    }
                } catch (IOException ioe) {
                    return true;
                } finally {
                    forceClose(urlc);
                }
            }
        }
        return false;
    }

    /**
     * This method closes a {@link URLConnection} by getting its {@link InputStream} and calling the
     * {@link InputStream#close()} method on it. The {@link URLConnection} doesn't have a close() method
     * and relies on garbage collection to close the underlying connection to the file.
     * Relying on garbage collection could lead to the application exhausting the number of files the
     * user is allowed to have open at any one point in time and cause the application to crash
     * ({@link FileNotFoundException} (Too many open files)).
     * Hence the need for this method to explicitly close the underlying connection to the file.
     *
     * @param urlConnection the {@link URLConnection} to be "closed" to close the underlying file descriptors.
     */
    private void forceClose(URLConnection urlConnection) {
        if (urlConnection != null) {
            // We need to get the input stream and close it to force the open
            // file descriptor to be released. Otherwise, we will reach the limit
            // for number of files open at one time.

            InputStream in = null;
            try {
                in = urlConnection.getInputStream();
            } catch (Exception e) {
                // Do nothing: We were not going to use it anyway.
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // Do nothing: Just want to make sure it is closed.
                    }
                }
            }
        }
    }

    /**
     * Run a script identified by name with a single argument.
     *
     * @param scriptName name of the script to run
     * @param argument   a single argument passed as a variable named <code>arg</code> in the binding
     * @return a <code>toString()</code> representation of the result of the execution of the script
     * @throws ResourceException if there is a problem accessing the script
     * @throws ScriptException   if there is a problem parsing the script
     */
    public String run(String scriptName, String argument) throws ResourceException, ScriptException {
        Binding binding = new Binding();
        binding.setVariable("arg", argument);
        Object result = run(scriptName, binding);
        return result == null ? "" : result.toString();
    }

    /**
     * Run a script identified by name with a given binding.
     *
     * @param scriptName name of the script to run
     * @param binding    the binding to pass to the script
     * @return an object
     * @throws ResourceException if there is a problem accessing the script
     * @throws ScriptException   if there is a problem parsing the script
     */
    public Object run(String scriptName, Binding binding) throws ResourceException, ScriptException {
        return createScript(scriptName, binding).run();
    }

    /**
     * Creates a Script with a given scriptName and binding.
     *
     * @param scriptName name of the script to run
     * @param binding    the binding to pass to the script
     * @return the script object
     * @throws ResourceException if there is a problem accessing the script
     * @throws ScriptException   if there is a problem parsing the script
     */
    public Script createScript(String scriptName, Binding binding) throws ResourceException, ScriptException {
        ScriptCacheEntry entry = updateCacheEntry(scriptName);
        scriptName = scriptName.intern();
        return InvokerHelper.createScript(entry.scriptClass, binding);
    }

    /**
     * Returns the GroovyClassLoader associated with this script engine instance.
     * Useful if you need to pass the class loader to another library.
     *
     * @return the GroovyClassLoader
     */
    public GroovyClassLoader getGroovyClassLoader() {
        return groovyLoader;
    }
}
