import com.sun.jdi.Bootstrap;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.request.EventRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Attaches a real JDI debugger, hits a plugin breakpoint, resumes, and detaches. */
class DebugProbe {
    public static void main(String[] args) throws Exception {
        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var options = connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1");
        options.get("port").setValue(args[0]);
        options.get("timeout").setValue("10000");
        var vm = connector.attach(options);
        try {
            var type = vm.classesByName("com.kaveenk.onlydragons.command.DevCommand").getFirst();
            var method = type.methodsByName("onCommand").getFirst();
            var breakpoint = vm.eventRequestManager().createBreakpointRequest(method.allLineLocations().getFirst());
            breakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            breakpoint.enable();
            var inbox = Path.of(args[1]);
            var id = UUID.randomUUID().toString();
            var temporary = inbox.resolve(id + ".tmp");
            Files.writeString(temporary, "onlydragons status\n");
            Files.move(temporary, inbox.resolve(id + ".cmd"));
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) {
                var events = vm.eventQueue().remove(1000);
                if (events == null) continue;
                boolean hit = false;
                for (var event : events) {
                    if (event instanceof BreakpointEvent bp) {
                        System.out.println("DEBUG_BREAKPOINT_OK " + bp.location());
                        hit = true;
                    }
                }
                events.resume();
                if (hit) return;
            }
            throw new IllegalStateException("Plugin breakpoint was not hit");
        } finally {
            vm.resume();
            vm.dispose();
        }
    }
}
