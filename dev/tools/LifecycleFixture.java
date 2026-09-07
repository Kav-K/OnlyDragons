import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * World-free stand-in process for lab shutdown and UTF-8 stream tests.
 * It deliberately emits a server-shaped ready marker but runs no Minecraft code.
 * Ignoring stop via the fixture property exercises verified process cleanup only.
 */
class LifecycleFixture {
    /**
     * Handles stdin stop/unicode probes until explicitly stopped; EOF alone keeps it alive.
     * @param args unused process arguments
     * @throws Exception on stream or sleep failure
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Done (0.1s)!");
        var input = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String command = input.readLine();
            if (command == null) {
                Thread.sleep(100);
            } else if (command.equals("unicode")) {
                System.out.println("fixture stdout: Dragon \u00b7 Idle \u2192 \u2713");
                System.err.println("fixture stderr: Dragon \u00b7 Idle \u2192 \u2713");
            } else if (command.equals("stop") && !Boolean.getBoolean("fixture.ignoreStop")) {
                System.out.println("Stopping server; fixture saved.");
                return;
            }
        }
    }
}
