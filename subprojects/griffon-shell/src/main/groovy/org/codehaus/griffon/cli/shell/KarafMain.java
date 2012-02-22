/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.codehaus.griffon.cli.shell;

import jline.Terminal;
import org.apache.felix.gogo.commands.Action;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.CommandException;
import org.apache.felix.gogo.commands.basic.AbstractCommand;
import org.apache.felix.gogo.runtime.CommandNotFoundException;
import org.apache.felix.gogo.runtime.CommandProcessorImpl;
import org.apache.felix.gogo.runtime.threadio.ThreadIOImpl;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Function;
import org.apache.felix.service.threadio.ThreadIO;
import org.apache.karaf.shell.console.NameScoping;
import org.apache.karaf.shell.console.jline.Console;
import org.apache.karaf.shell.console.jline.TerminalFactory;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static griffon.util.GriffonNameUtils.isBlank;

public class KarafMain {
    private String application = System.getProperty("karaf.name", "root");
    private String user = "karaf";

    /**
     * Use this method when the shell is being executed as a top level shell.
     *
     * @param args
     * @throws Exception
     */
    public void run(String args[]) throws Exception {
        ThreadIOImpl threadio = new ThreadIOImpl();
        threadio.start();

        CommandProcessorImpl commandProcessor = new CommandProcessorImpl(threadio);
        CommandProcessorImplHolder.setCommandProcessor(commandProcessor);

        ClassLoader cl = KarafMain.class.getClassLoader();
        if (args.length > 0 && args[0].startsWith("--classpath=")) {
            String base = args[0].substring("--classpath=".length());
            List<URL> urls = getFiles(new File(base));
            cl = new URLClassLoader(urls.toArray(new URL[urls.size()]), cl);
            String[] a = new String[args.length - 1];
            System.arraycopy(args, 1, a, 0, a.length);
            args = a;
        }

        discoverCommands(commandProcessor, cl);

        InputStream in = unwrap(System.in);
        PrintStream out = wrap(unwrap(System.out));
        PrintStream err = wrap(unwrap(System.err));
        run(commandProcessor, args, in, out, err);

        // TODO: do we need to stop the threadio that was started?
        // threadio.stop();
    }

    /**
     * Use this method when the shell is being executed as a command
     * of another shell.
     *
     * @param parent
     * @param args
     * @throws Exception
     */
    public void run(CommandSession parent, String args[]) throws Exception {
        // TODO: find out what the down side of not using a real ThreadIO implementation is.
        CommandProcessorImpl commandProcessor = new CommandProcessorImpl(new ThreadIO() {
            public void setStreams(InputStream in, PrintStream out, PrintStream err) {
            }

            public void close() {
            }
        });
        CommandProcessorImplHolder.setCommandProcessor(commandProcessor);

        ClassLoader cl = KarafMain.class.getClassLoader();
        if (args.length > 0 && args[0].startsWith("--classpath=")) {
            String base = args[0].substring("--classpath=".length());
            List<URL> urls = getFiles(new File(base));
            cl = new URLClassLoader(urls.toArray(new URL[urls.size()]), cl);
            String[] a = new String[args.length - 1];
            System.arraycopy(args, 1, a, 0, a.length);
            args = a;
        }

        discoverCommands(commandProcessor, cl);

        InputStream in = parent.getKeyboard();
        PrintStream out = parent.getConsole();
        PrintStream err = parent.getConsole();
        run(commandProcessor, args, in, out, err);
    }

    private void run(final CommandProcessorImpl commandProcessor, String[] args, final InputStream in, final PrintStream out, final PrintStream err) throws Exception {

        StringBuilder sb = new StringBuilder();
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(" ");
                }
                sb.append(args[i]);
            }
        }
        String stringifiedArgs = sb.toString().trim();

        if (!isBlank(stringifiedArgs)) {
            // Shell is directly executing a sub/command, we don't setup a terminal and console
            // in this case, this avoids us reading from stdin un-necessarily.
            CommandSession session = commandProcessor.createSession(in, out, err);
            session.put("USER", user);
            session.put("APPLICATION", application);
            session.put(NameScoping.MULTI_SCOPE_MODE_KEY, Boolean.toString(isMultiScopeMode()));

            try {
                session.execute(stringifiedArgs);
            } catch (Throwable t) {
                if (t instanceof CommandNotFoundException) {
                    String str = Ansi.ansi()
                            .fg(Ansi.Color.RED)
                            .a("Command not found: ")
                            .a(Ansi.Attribute.INTENSITY_BOLD)
                            .a(((CommandNotFoundException) t).getCommand())
                            .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                            .fg(Ansi.Color.DEFAULT).toString();
                    session.getConsole().println(str);
                } else if (t instanceof CommandException) {
                    session.getConsole().println(((CommandException) t).getNiceHelp());
                } else {
                    session.getConsole().print(Ansi.ansi().fg(Ansi.Color.RED).toString());
                    t.printStackTrace(session.getConsole());
                    session.getConsole().print(Ansi.ansi().fg(Ansi.Color.DEFAULT).toString());
                }
            }
        } else {
            // We are going into full blown interactive shell mode.

            final TerminalFactory terminalFactory = new TerminalFactory();
            final Terminal terminal = terminalFactory.getTerminal();
            Console console = createConsole(commandProcessor, in, out, err, terminal);
            CommandSession session = console.getSession();
            session.put("USER", user);
            session.put("APPLICATION", application);
            session.put(NameScoping.MULTI_SCOPE_MODE_KEY, Boolean.toString(isMultiScopeMode()));
            session.put("#LINES", new Function() {
                public Object execute(CommandSession session, List<Object> arguments) throws Exception {
                    return Integer.toString(terminal.getHeight());
                }
            });
            session.put("#COLUMNS", new Function() {
                public Object execute(CommandSession session, List<Object> arguments) throws Exception {
                    return Integer.toString(terminal.getWidth());
                }
            });
            session.put(".jline.terminal", terminal);

            console.run();

            terminalFactory.destroy();
        }
    }

    /**
     * Allow sub classes of main to change the Console implementation used.
     *
     * @param commandProcessor
     * @param in
     * @param out
     * @param err
     * @param terminal
     * @return
     * @throws Exception
     */
    protected Console createConsole(CommandProcessorImpl commandProcessor, InputStream in, PrintStream out, PrintStream err, Terminal terminal) throws Exception {
        return new Console(commandProcessor, in, out, err, terminal, null);
    }

    /**
     * Sub classes can override so that their registered commands do not conflict with the default shell
     * implementation.
     *
     * @return
     */
    public String getDiscoveryResource() {
        return "META-INF/services/org/apache/karaf/shell/commands";
    }

    protected void discoverCommands(CommandProcessorImpl commandProcessor, ClassLoader cl) throws IOException, ClassNotFoundException {
        Enumeration<URL> urls = cl.getResources(getDiscoveryResource());
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
            String line = r.readLine();
            while (line != null) {
                line = line.trim();
                if (line.length() > 0 && line.charAt(0) != '#') {
                    final Class<Action> actionClass = (Class<Action>) cl.loadClass(line);
                    Command cmd = actionClass.getAnnotation(Command.class);
                    Function function = new AbstractCommand() {
                        @Override
                        public Action createNewAction() {
                            try {
                                return ((Class<? extends Action>) actionClass).newInstance();
                            } catch (InstantiationException e) {
                                throw new RuntimeException(e);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    };
                    addCommand(cmd, function, commandProcessor);
                }
                line = r.readLine();
            }
            r.close();
        }
    }

    protected void addCommand(Command cmd, Function function, CommandProcessorImpl commandProcessor) {
        try {
            commandProcessor.addCommand(cmd.scope(), function, cmd.name());
        } catch (Exception e) {
            // ignore
        }
    }


    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Returns whether or not we are in multi-scope mode.
     * <p/>
     * The default mode is multi-scoped where we prefix commands by their scope. If we are in single
     * scoped mode then we don't use scope prefixes when registering or tab completing commands.
     */
    public boolean isMultiScopeMode() {
        return true;
    }

    private static PrintStream wrap(PrintStream stream) {
        OutputStream o = AnsiConsole.wrapOutputStream(stream);
        if (o instanceof PrintStream) {
            return ((PrintStream) o);
        } else {
            return new PrintStream(o);
        }
    }

    private static <T> T unwrap(T stream) {
        try {
            Method mth = stream.getClass().getMethod("getRoot");
            return (T) mth.invoke(stream);
        } catch (Throwable t) {
            return stream;
        }
    }

    private static List<URL> getFiles(File base) throws MalformedURLException {
        List<URL> urls = new ArrayList<URL>();
        getFiles(base, urls);
        return urls;
    }

    private static void getFiles(File base, List<URL> urls) throws MalformedURLException {
        for (File f : base.listFiles()) {
            if (f.isDirectory()) {
                getFiles(f, urls);
            } else if (f.getName().endsWith(".jar")) {
                urls.add(f.toURI().toURL());
            }
        }
    }
}
