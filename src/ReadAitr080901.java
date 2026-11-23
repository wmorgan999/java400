import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Properties;

/**
 * Test program: reads rows from a DB2 for i table and prints every column to the console.
 *
 * Usage: java -cp lib\jt400.jar;classes ReadAitr080901 [startRow]
 *   startRow - optional 1-based row position to start from (default 1)
 *
 * Settings: each value is taken from the environment variable first, then from
 * db.properties (or the file named by -Dconfig=path), then the built-in default.
 *
 *   env var       property      default
 *   DB_HOST       db.host       172.16.0.21
 *   DB_LIBRARY    db.library    NEWSYSGEN
 *   DB_TABLE      db.table      AITR080901
 *   DB_ROWS       db.rows       100
 *   DB_USER       db.user       (prompted if missing)
 *   DB_PASSWORD   db.password   (prompted if missing)
 */
public class ReadAitr080901 {

    public static void main(String[] args) throws Exception {
        long startRow = 1;
        if (args.length > 0) {
            try {
                startRow = Long.parseLong(args[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid start row: " + args[0]);
                System.exit(2);
            }
            if (startRow < 1) {
                System.err.println("Start row must be 1 or greater.");
                System.exit(2);
            }
        }

        Properties p = loadProperties(System.getProperty("config", "db.properties"));

        String host    = setting(p, "DB_HOST",     "db.host",     "172.16.0.21");
        String library = setting(p, "DB_LIBRARY",  "db.library",  "NEWSYSGEN");
        String table   = setting(p, "DB_TABLE",    "db.table",    "AITR080901");
        String user    = setting(p, "DB_USER",     "db.user",     null);
        String pw      = setting(p, "DB_PASSWORD", "db.password", null);
        int rows;
        try {
            rows = Integer.parseInt(setting(p, "DB_ROWS", "db.rows", "100"));
        } catch (NumberFormatException e) {
            System.err.println("db.rows / DB_ROWS must be a number.");
            System.exit(2);
            return;
        }

        Console console = System.console();
        if (isBlank(user) && console != null) {
            user = console.readLine("IBM i user: ");
        }
        if (isBlank(pw) && console != null) {
            char[] c = console.readPassword("IBM i password: ");
            pw = (c == null) ? null : new String(c);
        }
        if (isBlank(user) || isBlank(pw)) {
            System.err.println("No user/password: set DB_USER/DB_PASSWORD or db.user/db.password in db.properties.");
            System.exit(2);
        }

        long endRow = startRow + rows - 1;

        // prompt=false: never pop up the JTOpen sign-on dialog
        // naming=sql: LIBRARY.TABLE syntax
        String url = "jdbc:as400://" + host
                + ";naming=sql;libraries=" + library
                + ";prompt=false;translate binary=true;errors=full";

        // ROW_NUMBER() works on older IBM i releases (V5R4+), unlike OFFSET/FETCH (7.1 TR11+)
        String sql = "SELECT * FROM ("
                + " SELECT T.*, ROW_NUMBER() OVER () AS ZZ_ROWNUM"
                + " FROM " + library + "." + table + " T"
                + ") X WHERE ZZ_ROWNUM BETWEEN ? AND ? ORDER BY ZZ_ROWNUM";

        Class.forName("com.ibm.as400.access.AS400JDBCDriver");

        System.out.println("Connecting to " + host + " as " + user + " ...");
        try (Connection conn = DriverManager.getConnection(url, user, pw);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setReadOnly(true);
            ps.setLong(1, startRow);
            ps.setLong(2, endRow);

            System.out.println("Reading " + library + "." + table
                    + " rows " + startRow + " to " + endRow);

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount() - 1; // last column is ZZ_ROWNUM

                // Column layout
                System.out.println();
                System.out.println("Columns (" + cols + "):");
                int nameWidth = 0;
                for (int i = 1; i <= cols; i++) {
                    nameWidth = Math.max(nameWidth, md.getColumnName(i).length());
                }
                for (int i = 1; i <= cols; i++) {
                    System.out.printf("  %3d  %-" + nameWidth + "s  %-10s %d,%d  %s%n",
                            i, md.getColumnName(i), md.getColumnTypeName(i),
                            md.getPrecision(i), md.getScale(i),
                            nvl(md.getColumnLabel(i)));
                }

                // Rows
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println();
                    System.out.println("---- Row " + rs.getLong("ZZ_ROWNUM") + " ----");
                    for (int i = 1; i <= cols; i++) {
                        Object v = rs.getObject(i);
                        String s = (v == null) ? "<null>" : "[" + v.toString() + "]";
                        System.out.printf("  %-" + nameWidth + "s = %s%n", md.getColumnName(i), s);
                    }
                }

                System.out.println();
                System.out.println(count + " row(s) read.");
            }
        }
    }

    /** Environment variable wins, then the properties file, then the default. */
    private static String setting(Properties p, String envName, String propName, String def) {
        String v = System.getenv().getOrDefault(envName, p.getProperty(propName, def));
        if (isBlank(v)) {
            // an env var that exists but is blank should not hide the properties value
            v = p.getProperty(propName, def);
        }
        return isBlank(v) ? def : v.trim();
    }

    private static Properties loadProperties(String path) {
        Properties p = new Properties();
        File f = new File(path);
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
                System.out.println("Loaded settings from " + f.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("Could not read " + f.getAbsolutePath() + ": " + e.getMessage());
            }
        }
        return p;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
