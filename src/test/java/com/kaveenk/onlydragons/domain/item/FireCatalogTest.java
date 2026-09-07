package com.kaveenk.onlydragons.domain.item;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FireCatalogTest {
    @Test void flatRouterKeepsThreeExactHistoriesAndEnablesOnlyNewItems() {
        var router=CalibrationLoadouts.compatibleRegistry();
        assertEquals(Set.of("ordinary_v4","shortbow_v4","quiver_v4","flame_v4","duplex_flame_v4","tempo_flame_v4", "drawn_training_v4", "swift_shortbow_v4", "volley_shortbow_v4", "volley_ferocity25_v4", "volley_ferocity100_v4", "volley_duplex_v4", "volley_tempo_v4"),CalibrationLoadouts.fireRegistry().definitions().keySet());
        for(var concrete:List.of(CalibrationLoadouts.registry(),CalibrationLoadouts.expandedRegistry(),CalibrationLoadouts.fireRegistry()))
            for(var id:concrete.definitions().keySet()) {
                var item=concrete.create(id);assertEquals(concrete.resolve(item),router.resolve(item));
                assertEquals(concrete.revision(),router.create(id).registryRevision());
            }
        var item=router.create("ordinary_v4");
        assertEquals(Map.of("infinite_quiver",10,"flame",2),router.edit(item,Map.of("infinite_quiver",10,"flame",2),List.of()).enchantLevels());
        assertThrows(ItemValidationException.class,()->router.edit(item,Map.of("duplex",5,"fatal_tempo",5),List.of()));
        assertThrows(ItemValidationException.class,()->router.edit(item,Map.of("flame",3),List.of()));
        assertThrows(ItemValidationException.class,()->router.resolve(new ItemInstance(item.identity(),CalibrationLoadouts.EXPANDED_REVISION,Map.of(),List.of())));
        assertThrows(IllegalArgumentException.class,()->new ItemRegistry(List.of(router,CalibrationLoadouts.fireRegistry())));
        assertThrows(IllegalArgumentException.class,()->new ItemRegistry(List.of(CalibrationLoadouts.registry(),CalibrationLoadouts.registry())));
        assertThrows(IllegalArgumentException.class,()->new ItemRegistry(List.of()));
    }
}
