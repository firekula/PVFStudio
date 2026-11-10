package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CharacterAppearanceTest {
    @Test void acyclicIncludeFanoutIsBoundedAndKeepsTheValidBody() throws Exception {
        var data=new java.util.HashMap<String,String>();data.put("character/gunner/gunner.chr","[waiting motion] `body.ani`");data.put("character/gunner/body.ani","");data.put("equipment/l6.lay","");
        for(int i=0;i<6;i++)data.put("equipment/l"+i+".lay",("<include> `l"+(i+1)+".lay` ").repeat(4));
        var reads=new java.util.concurrent.atomic.AtomicInteger();var model=CharacterAppearance.load("equipment/test.equ","[equipment type] `[weapon]` <include> `l0.lay`",p->{reads.incrementAndGet();return data.get(p);},data.keySet(),"gunner","waiting motion",Map.of());
        assertTrue(reads.get()<2000,"Unbounded include lookup count: "+reads.get());assertTrue(model.parts().stream().anyMatch(p->p.slot().equals("body")));assertTrue(model.warnings().stream().anyMatch(w->w.contains("超限")));
    }
    private static Map<String,String> fixture() {
        var data=new HashMap<String,String>();
        data.put("character/gunner/gunner.chr","[waiting motion] `Animation/Stay.ani` [move motion] `Animation/Move.ani`");
        data.put("equipment/character/gunner.lay","[LAYER] 0 [waiting motion] `%s/Stay.ani` [move motion] `%s/Move.ani`");
        data.put("equipment/character/gunner/avatar/coat/test.equ","[equipment type] `[coat avatar]` 0 [variation] 41 2 [layer variation] 1800 `coat_a` <include> `equipment/character/gunner.lay` [layer variation] 900 `coat_b` <include> `equipment/character/gunner.lay`");
        return data;
    }
    @Test void expandsBothIncludeLayersWithIndependentVariationAndNativeOwnerPaths() throws Exception {
        var data=fixture();var path="equipment/character/gunner/avatar/coat/test.equ";
        var model=CharacterAppearance.load(path,data.get(path),data::get,data.keySet(),"gunner","waiting motion",Map.of());
        assertEquals(List.of(900,1000,1800),model.parts().stream().map(CharacterAppearance.Part::layer).toList());
        assertEquals("equipment/character/gunner/avatar/coat/coat_b/stay.ani",model.parts().get(0).animation());
        assertEquals(List.of(41,2),model.parts().get(2).variation());
        assertEquals("character/gunner/animation/stay.ani",model.parts().get(1).animation());
        assertTrue(model.actions().containsKey("move motion"));assertTrue(model.warnings().isEmpty());
    }
    @Test void selectedSlotReplacesDefaultAndSkinColorChangesBodyParameter() throws Exception {
        var data=fixture();var path="equipment/character/gunner/avatar/coat/test.equ";
        data.put("equipment/character/gunner/avatar/coat/acoat_default.equ","[equipment type] `[coat avatar]` 0 [waiting motion] `default.ani`");
        var skin="equipment/character/gunner/avatar/skin/skin.equ";data.put(skin,"[equipment type] `[skin avatar]` 0 [variation] 0 3");
        var model=CharacterAppearance.load(path,data.get(path),data::get,data.keySet(),"gunner","waiting motion",Map.of("skin avatar",skin));
        assertFalse(model.parts().stream().anyMatch(p->p.animation().endsWith("default.ani")));
        assertEquals(List.of(3),model.parts().stream().filter(p->p.slot().equals("body")).findFirst().orElseThrow().variation());
    }
    @Test void detectsRecursiveIncludeAndPreservesBodyInsteadOfHanging() throws Exception {
        var data=fixture();var path="equipment/character/gunner/avatar/coat/test.equ";
        data.put("equipment/character/gunner.lay","<include> `equipment/character/gunner.lay`");
        var model=CharacterAppearance.load(path,data.get(path),data::get,data.keySet(),"gunner","waiting motion",Map.of());
        assertTrue(model.parts().stream().anyMatch(p->p.slot().equals("body")));assertTrue(model.warnings().stream().anyMatch(w->w.contains("循环")));
    }
    @Test void explicitlyHiddenCurrentSlotStaysHiddenDuringPreviewReload() throws Exception {
        var data=fixture();var path="equipment/character/gunner/avatar/coat/test.equ";
        var model=CharacterAppearance.load(path,data.get(path),data::get,data.keySet(),"gunner","waiting motion",Map.of("coat avatar",""));
        assertTrue(model.parts().stream().noneMatch(p->p.slot().equals("coat avatar")));
    }
    @Test void missingIncludeDoesNotDiscardAFollowingValidLayer() throws Exception {
        var data=fixture();var path="equipment/character/gunner/avatar/coat/test.equ";
        data.put(path,"[equipment type] `[coat avatar]` [layer variation] 900 `coat_b` <include> `missing.lay` [layer variation] 1800 `coat_a` <include> `equipment/character/gunner.lay`");
        var model=CharacterAppearance.load(path,data.get(path),data::get,data.keySet(),"gunner","waiting motion",Map.of());
        assertTrue(model.parts().stream().anyMatch(p->p.layer()==1800&&p.animation().endsWith("coat_a/stay.ani")));assertFalse(model.warnings().isEmpty());
    }
    @Test void hideLayerAffectsOtherSlotsButNotItsOwnAttachment() throws Exception {
        var data=fixture();var path="equipment/character/gunner/avatar/coat/test.equ";
        data.put(path,data.get(path)+" [hide layer] 1000");
        var model=CharacterAppearance.load(path,data.get(path),data::get,data.keySet(),"gunner","waiting motion",Map.of());
        assertEquals(2,model.parts().size());assertFalse(model.parts().stream().anyMatch(p->p.slot().equals("body")));
    }
}
