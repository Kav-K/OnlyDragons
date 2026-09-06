import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Small server-process fixture for testing shutdown without creating Minecraft worlds. */
class LifecycleFixture {
    public static void main(String[] args) throws Exception {
        System.out.println("Done (0.1s)!");
        var input = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String command = input.readLine();
            if (command == null) {
                Thread.sleep(100);
            } else if (command.equals("stop") && !Boolean.getBoolean("fixture.ignoreStop")) {
                System.out.println("Stopping server; fixture saved.");
                return;
            }
        }
    }
}
