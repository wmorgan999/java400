import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

/**
 * Test program: reads 100 rows from NEWSYSGEN.AITR080901 on an IBM i (DB2 for i)
 * and prints every column to the console.
 *
 * Usage: java -cp lib\jt400.jar;classes ReadAitr080901 [startRow]
 *   startRow - optional 1-based row position to start from (default 1)
 *
 * Credentials come from environment variables AS400_USER and AS400_PASSWORD.
 */
public class ReadAitr080901 {

    private static final String HOST    = "172.16.0.21";
    private static final String LIBRARY = "NEWSYSGEN";
    private static final String TABLE   = "AITR080901";
    private static final int    ROWS    = 100;

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

        String user = System.getenv("AS400_USER");
        String pass = System.getenv("AS400_PASSWORD");
        if (user == null || user.isEmpty() || pass == null || pass.isEmpty()) {
            System.err.println("Set AS400_USER and AS400_PASSWORD environment variables.");
            System.exit(2);
        }

        long endRow = startRow + ROWS - 1;

        // prompt=false: never pop up the JTOpen sign-on dialog
        // naming=sql: LIBRARY.TABLE syntax
        String url = "jdbc:as400://" + HOST
                + ";naming=sql;libraries=" + LIBRARY
                + ";prompt=false;translate binary=true;errors=full";

        // ROW_NUMBER() works on older IBM i releases (V5R4+), unlike OFFSET/FETCH (7.1 TR11+)
        String sql = "SELECT * FROM ("
                + " SELECT T.*, ROW_NUMBER() OVER () AS ZZ_ROWNUM"
                + " FROM " + LIBRARY + "." + TABLE + " T"
                + ") X WHERE ZZ_ROWNUM BETWEEN ? AND ? ORDER BY ZZ_ROWNUM";

        Class.forName("com.ibm.as400.access.AS400JDBCDriver");

        System.out.println("Connecting to " + HOST + " as " + user + " ...");
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setReadOnly(true);
            ps.setLong(1, startRow);
            ps.setLong(2, endRow);

            System.out.println("Reading " + LIBRARY + "." + TABLE
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

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
