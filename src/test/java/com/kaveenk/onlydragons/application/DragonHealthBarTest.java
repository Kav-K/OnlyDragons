package com.kaveenk.onlydragons.application;

import java.util.*;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests one Adventure bar identity per generation, domain-zero retention, exact-session
 * replacement and failure-isolated removal. In-memory Audience doubles record API calls and
 * shared BossBar references; they are not packet receivers and cannot certify client appearance.
 * Also checks locale-independent display rounding and clamped health progress.
 */
class DragonHealthBarTest {
    /**
     * In-memory delivery double retaining BossBar object references; updates are inspected on
     * the same object, so this does not imitate packet snapshots.
     */
    static final class Client implements Audience {
        final List<BossBar> adds = new ArrayList<>(), removes = new ArrayList<>();
        /**
         * Records the exact presented bar reference without emitting network traffic.
         */
        public void showBossBar(BossBar bar) { adds.add(bar); }
        /** Records the exact removal target for identity and idempotent-close assertions. */
        public void hideBossBar(BossBar bar) { removes.add(bar); }
    }
    @Test void identityHealthZeroAndRetirementAreIndependentOfCredit() {
        var ui = new DragonHealthBar(); var a = new Client(); var b = new Client();
        var viewers = List.of(new DragonHealthBar.Viewer(UUID.randomUUID(), a), new DragonHealthBar.Viewer(UUID.randomUUID(), b));
        UUID generation = UUID.randomUUID();
        ui.reconcile(Optional.of(new DragonHealthBar.Display(generation,"Dragon",1000,1000)),viewers);
        BossBar bar = a.adds.getFirst(); assertSame(bar,b.adds.getFirst());
        for (int i=0;i<20;i++) ui.reconcile(Optional.of(new DragonHealthBar.Display(generation,"Dragon",400,1000)),viewers);
        assertEquals(1,a.adds.size()); assertEquals(.4f,bar.progress());
        ui.reconcile(Optional.of(new DragonHealthBar.Display(generation,"Dragon",0,1000)),viewers);
        assertEquals(0,bar.progress()); assertTrue(a.removes.isEmpty());
        assertTrue(bar.flags().isEmpty());
        ui.reconcile(Optional.empty(),viewers);
        assertEquals(List.of(bar),a.removes); assertEquals(List.of(bar),b.removes);
        assertEquals(0,ui.viewerCount()); assertTrue(ui.generation().isEmpty());
    }
    @Test void reconnectWorldTransferGenerationAndCloseReleaseExactSessions() {
        var ui = new DragonHealthBar(); var old = new Client(); var fresh = new Client(); UUID id=UUID.randomUUID();
        var first = new DragonHealthBar.Display(UUID.randomUUID(),"Dragon",10,10);
        ui.reconcile(Optional.of(first),List.of(new DragonHealthBar.Viewer(id,old)));
        ui.reconcile(Optional.of(first),List.of(new DragonHealthBar.Viewer(id,fresh)));
        assertEquals(old.adds,old.removes); assertSame(old.adds.getFirst(),fresh.adds.getFirst());
        ui.reconcile(Optional.of(first),List.of()); assertEquals(fresh.adds,fresh.removes);
        ui.reconcile(Optional.of(first),List.of(new DragonHealthBar.Viewer(id,fresh)));
        var next = new DragonHealthBar.Display(UUID.randomUUID(),"Next",5,10);
        ui.reconcile(Optional.of(next),List.of(new DragonHealthBar.Viewer(id,fresh)));
        assertNotSame(fresh.adds.getFirst(),fresh.adds.getLast());
        ui.close(); ui.close();
        assertEquals(fresh.adds,fresh.removes);
        ui.reconcile(Optional.of(next),List.of(new DragonHealthBar.Viewer(id,fresh)));
        assertEquals(0,ui.viewerCount());
    }
    @Test void throwingAudienceCannotPreventOtherRemovalOrRetirement() {
        var ui=new DragonHealthBar();var healthy=new Client();
        Audience broken=new Audience(){/** Throws on removal to prove one failed delivery cannot prevent other cleanup. */ public void hideBossBar(BossBar bar){throw new IllegalStateException("fixture");}};
        ui.reconcile(Optional.of(new DragonHealthBar.Display(UUID.randomUUID(),"Dragon",1,1)),List.of(new DragonHealthBar.Viewer(UUID.randomUUID(),broken),new DragonHealthBar.Viewer(UUID.randomUUID(),healthy)));
        ui.close();ui.close();
        assertEquals(healthy.adds,healthy.removes);assertEquals(1,ui.deliveryFailures());
        assertEquals(0,ui.viewerCount());assertTrue(ui.generation().isEmpty());
    }
    @Test void formattingIsLocaleIndependentAndHealthIsClampedWithoutChangingInputs() {
        assertEquals("1,234.57",PresentationFormatter.number(1234.567));
        assertEquals("0",PresentationFormatter.number(-0.0));
        assertEquals("Fatal Tempo",PresentationFormatter.label("fatal_tempo"));
        assertEquals(1,PresentationFormatter.healthProgress(12,10));
        assertEquals(0,PresentationFormatter.healthProgress(-1,10));
        assertThrows(IllegalArgumentException.class,()->PresentationFormatter.healthProgress(1,0));
        assertThrows(IllegalArgumentException.class,()->PresentationFormatter.number(Double.NaN));
    }
}
