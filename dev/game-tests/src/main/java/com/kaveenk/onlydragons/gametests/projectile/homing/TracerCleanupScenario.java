package com.kaveenk.onlydragons.gametests.projectile.homing;

import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.gametests.*;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.util.BoundingBox;

/**
 * Deliberate abort with real production shared-ticket demand and no fake projectile admission.
 * Two consumers share a ticket footprint; releasing one must retain the other.
 * Abort releases owned demand/reservations/tasks while preserving a preexisting
 * native ticket until the fixture releases only the ticket it added itself.
 */
public final class TracerCleanupScenario implements Scenario {
    /**
     * Registers cleanup before creating shared production ticket demand, then aborts explicitly.
     * @param context server-thread report/resource owner
     */
    @Override public void start(ScenarioContext context) {
        context.mechanicRevision("tracer-cleanup-v1");
        var bows = context.production().bows(); var world = Bukkit.getWorlds().getFirst(); UUID arena = UUID.randomUUID();
        boolean[] borrowed = {false};
        context.cleanup("tracer-abort-service", () -> {
            bows.endEncounter(arena);
            context.check("abort_releases_tickets_and_reservation", true, bows.continuity().tickets().ticketCount() == 0
                    && bows.continuity().tickets().reservedCount() == 0 && bows.continuity().tickets().demandCount() == 0);
            bows.close(); context.check("abort_releases_owned_task", 0, bows.taskCount());
            context.check("abort_preserves_preexisting_native_ticket", true, world.getPluginChunkTickets(0, 0).contains(context.production()));
            if (borrowed[0]) world.removePluginChunkTicket(0, 0, context.production());
        });
        bows.openEncounter(arena, world, new BoundingBox(0, 60, 0, 16, 200, 16), new MechanicRevision("tracer-fixture", "v1"));
        borrowed[0] = world.addPluginChunkTicket(0, 0, context.production());
        var tickets = bows.continuity().tickets();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        tickets.retain(first, arena, 0, 0); tickets.retain(second, arena, 0, 0);
        context.check("abort_has_live_shared_ticket_demand", true, tickets.ticketCount() == 9 && tickets.demandCount() == 2
                && world.getPluginChunkTickets(0, 0).contains(context.production()));
        tickets.release(first);
        context.check("abort_other_consumer_keeps_native_ticket", true, tickets.ticketCount() == 9 && tickets.demandCount() == 1
                && world.getPluginChunkTickets(0, 0).contains(context.production()));
        context.abort();
    }
}
