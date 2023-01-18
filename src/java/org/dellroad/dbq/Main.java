
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.dbq;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.dellroad.stuff.io.NullModemInputStream;
import org.dellroad.stuff.io.WriteCallback;
import org.dellroad.stuff.main.MainClass;
import org.dellroad.stuff.sql.XMLResultSetWriter;
import org.dellroad.stuff.xml.TransformErrorListener;
import org.slf4j.LoggerFactory;

public class Main extends MainClass {

    private static final String SAXON_TRANSFORMER_FACTORY_CLASS_NAME = "net.sf.saxon.TransformerFactoryImpl";

    private static final int OUTPUT_XML = 0;
    private static final int OUTPUT_CSV = 1;
    private static final int OUTPUT_HTML = 2;

    private static final HashMap<String, String> DRIVER_CLASSES = new HashMap<String, String>();
    static {
        DRIVER_CLASSES.put("mssql", "net.sourceforge.jtds.jdbc.Driver");
        DRIVER_CLASSES.put("mysql", "com.mysql.cj.jdbc.Driver");
        DRIVER_CLASSES.put("odbc", "sun.jdbc.odbc.JdbcOdbcDriver");
        DRIVER_CLASSES.put("oracle", "oracle.jdbc.OracleDriver");
    }

    private static final int BUFFER_SIZE = 1000;
    private static final int DEFAULT_INDENT = 4;

    private String driverClass = DRIVER_CLASSES.get("odbc");
    private String url;
    private String user;
    private String pass;
    private String file;
    private String query;
    private int outputStyle = OUTPUT_XML;
    private int indent = DEFAULT_INDENT;
    private File sqlFile;
    private File xsltFile;
    private boolean columnNames;

    @Override
    public int run(String[] args) throws Exception {

        // Parse command line
        ArrayDeque<String> params = new ArrayDeque<String>(Arrays.asList(args));
        while (!params.isEmpty() && params.peekFirst().startsWith("-")) {
            String option = params.removeFirst();
            if ((option.equals("-U") || option.equals("--url")) && !params.isEmpty())
                this.url = params.removeFirst();
            else if (option.equals("-c") || option.equals("--colnames"))
                this.columnNames = true;
            else if (option.equals("-C") || option.equals("--csv"))
                this.outputStyle = OUTPUT_CSV;
            else if (option.equals("-H") || option.equals("--html"))
                this.outputStyle = OUTPUT_HTML;
            else if ((option.equals("-u") || option.equals("--user")) && !params.isEmpty())
                this.user = params.removeFirst();
            else if (option.equals("-p") || (option.equals("--pass")) && !params.isEmpty())
                this.pass = params.removeFirst();
            else if (option.equals("-d") || (option.equals("--driver")) && !params.isEmpty())
                this.driverClass = params.removeFirst();
            else if (option.equals("-f") || (option.equals("--file")) && !params.isEmpty())
                this.sqlFile = new File(params.removeFirst());
            else if (option.equals("-x") || (option.equals("--xslt")) && !params.isEmpty())
                this.xsltFile = new File(params.removeFirst());
            else if (option.equals("--"))
                break;
            else {
                System.err.println("dbq: unknown option `" + option + "'");
                this.usageError();
                return 1;
            }
        }

        // Sanity check
        if (this.sqlFile != null && !params.isEmpty()) {
            System.err.println("dbq: cannot mix `-f' flag and command line arguments");
            this.usageError();
            return 1;
        }
        if (this.outputStyle != OUTPUT_XML && this.xsltFile != null) {
            System.err.println("dbq: cannot mix `-x' and `-C' or `-H'");
            this.usageError();
            return 1;
        }
        if (this.driverClass == null) {
            System.err.println("dbq: no driver specified");
            this.usageError();
            return 1;
        }
        if (this.url == null) {
            System.err.println("dbq: no URL specified");
            this.usageError();
            return 1;
        }

        // Get query
        switch (params.size()) {
        case 0:
            {
                StringWriter sql = new StringWriter();
                Reader reader = new InputStreamReader(new BufferedInputStream(
                  this.sqlFile != null ? new FileInputStream(this.sqlFile) : System.in));
                char[] buf = new char[BUFFER_SIZE];
                int r;
                while ((r = reader.read(buf)) != -1)
                    sql.write(buf, 0, r);
                this.query = sql.toString();
                break;
            }
        default:
            {
                StringBuilder sql = new StringBuilder();
                while (!params.isEmpty()) {
                    if (sql.length() > 0)
                        sql.append(' ');
                    sql.append(params.removeFirst());
                }
                this.query = sql.toString();
                break;
            }
        }

        // Load driver
        String actual = DRIVER_CLASSES.get(this.driverClass);
        if (actual != null)
            this.driverClass = actual;
        try {
            Class.forName(this.driverClass);
        } catch (Exception e) {
            System.err.println("Error: can't load driver class " + this.driverClass);
            e.printStackTrace(System.err);
            return 1;
        }

        // Set up properties
        Properties props = new Properties();
        if (this.user != null) {
            props.put("username", this.user);
            props.put("user", this.user);
        }
        if (this.pass != null)
            props.put("password", this.pass);

        // Connect to database
        Driver driver;
        try {
            driver = DriverManager.getDriver(this.url);
        } catch (Exception e) {
            System.err.println("Error: can't get driver for URL `" + this.url + "': " + e);
            return 1;
        }
        Connection connection = driver.connect(this.url, props);
        Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

        // Perform query
        final ResultSet resultSet = statement.executeQuery(this.query);

        // Write out results
        if (this.outputStyle == OUTPUT_XML && this.xsltFile == null)
            this.writeXML(resultSet, System.out);
        else {

            // Get XSLT file
            if (this.xsltFile == null) {
                for (String elem : System.getProperty("java.class.path").split(System.getProperty("path.separator"))) {
                    if (elem.endsWith("dbq.jar")) {
                        this.xsltFile = new File(elem.replaceAll("dbq.jar$",
                          this.outputStyle == OUTPUT_CSV ? "csv.xsl" : "html.xsl"));
                        break;
                    }
                }
            }
            if (this.xsltFile == null || !this.xsltFile.exists())
                throw new RuntimeException("can't find XSLT transform file: " + this.xsltFile);

            // Get TransformerFactory; prefer Saxon if found
            TransformerFactory transformerFactory = null;
            boolean isXalan = false;
            try {
                transformerFactory = TransformerFactory.newInstance(
                  SAXON_TRANSFORMER_FACTORY_CLASS_NAME, Thread.currentThread().getContextClassLoader());
            } catch (Exception e) {
                transformerFactory = TransformerFactory.newInstance();
                isXalan = true;
            }
            transformerFactory.setErrorListener(new TransformErrorListener(LoggerFactory.getLogger(this.getClass()), isXalan));

            // Create transformer
            final InputStream xslInput = new BufferedInputStream(new FileInputStream(this.xsltFile));
            final Transformer transformer = transformerFactory.newTransformer(
              new StreamSource(xslInput, xsltFile.toURI().toString()));
            xslInput.close();

            // Transform result
            transformer.transform(new StreamSource(new NullModemInputStream(new WriteCallback() {
                @Override
                public void writeTo(OutputStream output) throws IOException {
                    try {
                        Main.this.writeXML(resultSet, output);
                    } catch (Exception e) {
                        throw new IOException("error reading SQL result set", e);
                    }
                }
            }, "XML Input")), new StreamResult(System.out));
        }

        // Close query
        resultSet.close();
        statement.close();
        connection.close();

        // Done
        return 0;
    }

    private void writeXML(ResultSet resultSet, OutputStream out) throws SQLException, IOException, XMLStreamException {
        BufferedOutputStream output = new BufferedOutputStream(out, BUFFER_SIZE);
        XMLStreamWriter xmlWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8");
        XMLResultSetWriter resultSetWriter = new XMLResultSetWriter(xmlWriter, this.indent);
        resultSetWriter.setColumnNameTags(this.columnNames);
        resultSetWriter.write(this.query.trim(), resultSet);
        xmlWriter.close();
        output.close();
    }

    @Override
    protected void usageMessage() {
        System.err.println("Usage: dbq [options] --driver driver --url URL [query...]");
        System.err.println("Options:");
        System.err.println("  -c, --colnames    Use column names as XML element names");
        System.err.println("  -C, --csv         Output result set as CSV");
        System.err.println("  -d, --driver      Specify driver class name (or one of " + DRIVER_CLASSES.keySet() + ")");
        System.err.println("  -f, --file        Read SQL query from specified file instead of stdin");
        System.err.println("  -H, --html        Output result set as HTML");
        System.err.println("  -p, --pass        Specify database password");
        System.err.println("  -u, --user        Specify database username");
        System.err.println("  -U, --url         Specify JDBC database URL");
        System.err.println("  -x, --xsl         Apply specified XSLT to result set");
        System.err.println("SQL query is read from stdin if not specified on the command line");
    }

    public static void main(String[] args) throws Exception {
        new Main().doMain(args);
    }
}

