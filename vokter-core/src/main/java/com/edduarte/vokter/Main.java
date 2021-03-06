/*
 * Copyright 2015 Eduardo Duarte
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.edduarte.vokter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Eduardo Duarte (<a href="mailto:hello@edduarte.com">hello@edduarte.com</a>)
 * @version 1.3.2
 * @since 1.0.0
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);


    public static void main(String[] args) {

        installUncaughtExceptionHandler();

        CommandLineParser parser = new GnuParser();
        Options options = new Options();

        options.addOption("t", "threads", true, "Number of threads to be used "
                + "for computation and indexing processes. Defaults to the number "
                + "of available cores.");

        options.addOption("p", "port", true, "Core server port. Defaults to 9000.");

        options.addOption("dbh", "db-host", true, "Database host. Defaults to localhost.");

        options.addOption("dbp", "db-port", true, "Database port. Defaults to 27017.");

        options.addOption("case", "preserve-case", false, "Keyword matching with case sensitivity.");

        options.addOption("stop", "stopwords", false, "Keyword matching with stopword filtering.");

        options.addOption("stem", "stemming", false, "Keyword matching with stemming (lexical variants).");

        options.addOption("h", "help", false, "Shows this help prompt.");


        CommandLine commandLine;
        try {
            // Parse the program arguments
            commandLine = parser.parse(options, args);
        } catch (ParseException ex) {
            logger.error("There was a problem processing the input arguments. " +
                    "Write -h or --help to show the list of available commands.");
            logger.error(ex.getMessage(), ex);
            return;
        }


        if (commandLine.hasOption('h')) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar vokter-core.jar", options);
            return;
        }

        int maxThreads = Runtime.getRuntime().availableProcessors() - 1;
        maxThreads = maxThreads > 0 ? maxThreads : 1;
        if (commandLine.hasOption('t')) {
            String threadsText = commandLine.getOptionValue('t');
            maxThreads = Integer.parseInt(threadsText);
            if (maxThreads <= 0 || maxThreads > 32) {
                logger.error("Invalid number of threads. Must be a number between 1 and 32.");
                return;
            }
        }

        int port = 9000;
        if (commandLine.hasOption('p')) {
            String portString = commandLine.getOptionValue('p');
            port = Integer.parseInt(portString);
        }

        String dbHost = "localhost";
        if (commandLine.hasOption("dbh")) {
            dbHost = commandLine.getOptionValue("dbh");
        }

        int dbPort = 27017;
        if (commandLine.hasOption("dbp")) {
            String portString = commandLine.getOptionValue("dbp");
            dbPort = Integer.parseInt(portString);
        }

        boolean isIgnoringCase = true;
        if (commandLine.hasOption("case")) {
            isIgnoringCase = false;
        }

        boolean isStoppingEnabled = false;
        if (commandLine.hasOption("stop")) {
            isStoppingEnabled = true;
        }

        boolean isStemmingEnabled = false;
        if (commandLine.hasOption("stem")) {
            isStemmingEnabled = true;
        }

        try {
            Context context = Context.getInstance();
            context.setIgnoreCase(isIgnoringCase);
            context.setStopwordsEnabled(isStoppingEnabled);
            context.setStemmingEnabled(isStemmingEnabled);
            context.start(port, maxThreads, dbHost, dbPort);

        } catch (Exception ex) {
            ex.printStackTrace();
            logger.info("Shutting down the server...");
            System.exit(1);
        }
    }


    private static void installUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (e instanceof ThreadDeath) {
                logger.warn("Ignoring uncaught ThreadDead exception.");
                return;
            }
            logger.error("Uncaught exception on cli thread, aborting.", e);
            System.exit(0);
        });
    }
}
