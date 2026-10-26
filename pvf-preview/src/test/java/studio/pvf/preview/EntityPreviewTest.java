package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EntityPreviewTest {
    @Test void actionBodySubLayersAndOffsetsRenderForEveryFlattenedMapSpawn() throws Exception {
        Map<String,String> data=base();data.put("monster/monster.lst","1 `g/a.mob`");data.put("passiveobject/passiveobject.lst","2 `g/b.obj`");
        data.put("monster/g/a.mob","[waiting action] `action/stay.act`");data.put("passiveobject/g/b.obj","[basic action] `action/stay.act`");
        for(String prefix:List.of("monster/g/","passiveobject/g/")) {
            data.put(prefix+"action/stay.act","[MOTION] [BASE ANI] `../animation/body.ani` [SUB ANI] `../animation/back.ani` 0 -10 [/SUB ANI] "
                    +"[SUB ANI WITH XYZ] `../animation/front.ani` 0 100 3 2 5 [/SUB ANI WITH XYZ] [SUB ANI] `../animation/delayed.ani` 1 101 [/SUB ANI] [/MOTION] "
                    +"[BEHAVIOR] [SET ACTION] `never.act` [/BEHAVIOR]");
            data.put(prefix+"animation/body.ani",ani("green.img",0,-4,1));data.put(prefix+"animation/back.ani",ani("blue.img",-1,-5,1));
            data.put(prefix+"animation/front.ani",ani("yellow.img",0,-3,1));
        }
        var frame=scene(data,"[monster] 1 1 0 20 30 0 1 1 `[fixed]` `[normal]` 1 1 0 60 30 0 1 1 `[fixed]` `[normal]` [/monster] "
                +"[passive object] 2 100 30 0 2 140 30 0 [/passive object]").render(0,false);
        assertEquals(4,frame.entities().size());assertEquals(12,frame.draws().size());
        assertTrue(frame.entities().stream().allMatch(e->e.problem().isEmpty()&&e.info().animations().size()==3));
        assertTrue(frame.warnings().stream().allMatch(w->w.contains("非零起始帧")));
        var image=paint(frame);assertEquals(Color.BLUE.getRGB(),image.getRGB(19,145));assertEquals(Color.GREEN.getRGB(),image.getRGB(20,146));
        assertEquals(Color.YELLOW.getRGB(),image.getRGB(23,144));assertEquals(Color.YELLOW.getRGB(),image.getRGB(143,144));
    }
    @Test void clientGolemRecipesSupplyMissingLimbsAndReplaceInheritedSteelSlots() throws Exception {
        Map<String, String> data = base(); data.put("monster/monster.lst", "31 `stone.mob` 34 `bronze.mob`");
        data.put("monster/stone.mob", "[waiting motion] `body.ani`"); data.put("monster/bronze.mob", "[waiting motion] `body.ani`");
        data.put("monster/body.ani", ani("green.img", 0, -4, 1));
        for (String material : List.of("sg", "st", "br")) {
            int i = 0;
            for (String limb : List.of("sthead", "stleftarm", "strightarm", "stleg")) {
                String path = "equipment/monster/golem/" + material + "/" + limb;
                data.put(path + ".equ", "[equipment type] `[slot" + i + "]` 0 [layer] " + (i++ * 100 - 200)
                        + " [waiting motion] `" + limb + ".ani`");
                data.put(path + ".ani", ani(material.equals("br") ? "yellow.img" : "blue.img", 0, -4, 1));
            }
        }
        var frame = scene(data, "[monster]\n31 1 0 20 30 0 1 1 `[fixed]` `[normal]`\n"
                + "34 1 0 50 30 0 1 1 `[fixed]` `[normal]`\n[/monster]").render(0, false);
        assertTrue(frame.warnings().isEmpty());
        for (var entity : frame.entities()) assertEquals(5, entity.info().animations().size());
        assertTrue(frame.entities().get(1).info().animations().stream().noneMatch(path -> path.contains("/st/")));
        assertEquals(Color.YELLOW.getRGB(), paint(frame).getRGB(50, 146));
    }
    @Test void eachEquipmentVariationIsResolvedIndependentlyOfSceneDefaults() throws Exception {
        Map<String, String> data = base(); data.put("monster/monster.lst", "1 `a.mob`");
        data.put("monster/a.mob", "[waiting motion] `body.ani` [equipment] 2 `front.equ` `back.equ` [/equipment]");
        data.put("monster/body.ani", ani("green.img", 0, 0, 1));
        data.put("equipment/front.equ", "[variation] 3 2 [waiting motion] `front.ani`");
        data.put("equipment/back.equ", "[variation] 1 4 [waiting motion] `front.ani`");
        data.put("equipment/front.ani", ani("part%02d%02d.img", 0, -4, 1));
        var frame = scene(data, "[monster]\n1 1 0 20 30 0 1 1 `[fixed]` `[normal]`\n[/monster]").render(0, false);
        assertTrue(frame.warnings().isEmpty());
        var names = frame.draws().stream().map(d -> d.prepared().source().img()).toList();
        assertTrue(names.contains("part0302.img")); assertTrue(names.contains("part0104.img"));
    }
    @Test void npcFacingMirrorsAroundFeetAndUsesMapHeight() throws Exception {
        Map<String, String> data = base(); data.put("npc/npc.lst", "4 `a.npc`");
        data.put("npc/a.npc", "[field animation] `ani/a.ani`"); data.put("npc/ani/a.ani", ani("pattern.img", 0, -4, 1));
        var frame = scene(data, "[NPC]\n4 `[left]` 20 30 5\n4 `[right]` 40 30 0\n[/NPC]").render(0, false);
        BufferedImage image = paint(frame);
        assertEquals(Color.RED.getRGB(), image.getRGB(19, 142)); assertEquals(Color.GREEN.getRGB(), image.getRGB(16, 142));
        assertEquals(Color.RED.getRGB(), image.getRGB(40, 147)); assertEquals(0, image.getRGB(20, 142));
        assertTrue(frame.entityAt(19, 142).contains("NPC #4")); assertTrue(frame.entityAt(19, 142).contains("左向"));
    }
    @Test void monsterBodyAndEquipmentAreOrderedWithinOneDepthGroup() throws Exception {
        Map<String, String> data = base(); data.put("monster/monster.lst", "1 `g/g.mob`");
        data.put("monster/g/g.mob", "[waiting motion] `body.ani` [equipment] 2 `Monster/back.equ` `Monster/front.equ` [/equipment]");
        data.put("monster/g/body.ani", ani("green.img", 0, -4, 1));
        data.put("equipment/monster/back.equ", "[LAYER] -10 [waiting motion] `back.ani`");
        data.put("equipment/monster/back.ani", ani("blue.img", -1, -5, 1));
        data.put("equipment/monster/front.equ", "[LAYER] 100 [waiting motion] `front.ani`");
        data.put("equipment/monster/front.ani", ani("yellow.img", 1, -3, 1));
        var frame = scene(data, "[monster]\n1 1 0 20 30 0 1 1 `[fixed]` `[normal]`\n[/monster]").render(0, false);
        assertEquals(3, frame.entities().get(0).info().animations().size());
        var image = paint(frame); assertEquals(Color.BLUE.getRGB(), image.getRGB(19, 145));
        assertEquals(Color.GREEN.getRGB(), image.getRGB(20, 146)); assertEquals(Color.YELLOW.getRGB(), image.getRGB(21, 147));
    }
    @Test void entitiesShareSceneDepthWithAnimationsRatherThanAlwaysPaintingLast() throws Exception {
        Map<String, String> data = base(); data.put("npc/npc.lst", "1 `red.npc` 2 `green.npc`");
        data.put("npc/red.npc", "[field animation] `red.ani`"); data.put("npc/red.ani", ani("red.img", 0, -4, 1));
        data.put("npc/green.npc", "[field animation] `green.ani`"); data.put("npc/green.ani", ani("green.img", 0, -4, 1));
        data.put("map/cover.ani", ani("blue.img", 0, -4, 1));
        var frame = scene(data, "[animation] `cover.ani` `[normal]` 20 34 0 [/animation]\n"
                + "[NPC]\n1 `[right]` 20 32 0\n2 `[right]` 20 30 0\n[/NPC]").render(0, false);
        var image = paint(frame); assertEquals(Color.RED.getRGB(), image.getRGB(20, 149));
        assertEquals(Color.BLUE.getRGB(), image.getRGB(20, 151));
    }
    @Test void specialObjectsDoNotSpawnTheirLootAndConditionalMonstersNeedOptIn() throws Exception {
        Map<String, String> data = base(); data.put("passiveobject/passiveobject.lst", "221 `barrel.obj`");
        data.put("passiveobject/barrel.obj", "[layer] `[bottom]` [basic motion] `barrel.ani`");
        data.put("passiveobject/barrel.ani", ani("blue.img", 0, -4, 1));
        data.put("monster/monster.lst", "1 `a.mob`"); data.put("monster/a.mob", "[waiting motion] `a.ani`"); data.put("monster/a.ani", ani("green.img", 0, -4, 1));
        var scene = scene(data, "[special passive object]\n221 20 30 0\n1\n`[monster]` 999 20 -1 -1 -1\n[/special passive object]\n"
                + "[monster condition]\n1 1 0 30 40 0 1 1 `[fixed]` `[normal]`\n[/monster condition]");
        assertEquals(1, scene.render(0, false).entities().size()); assertEquals(2, scene.render(0, false, true).entities().size());
        assertEquals(1, scene.render(0, false).conditionalCount()); assertEquals(221, scene.render(0, false).entities().get(0).info().spawn().id());
    }
    @Test void missingDefinitionsAndLogicObjectsHaveMarkersWithoutHidingGoodEntities() throws Exception {
        Map<String, String> data = base(); data.put("passiveobject/passiveobject.lst", "1 `logic.obj` 2 `solid.obj`");
        data.put("passiveobject/logic.obj", "[basic motion] `` [etc motion] `destroy.ani` [/etc motion]");
        data.put("passiveobject/solid.obj", "[basic motion] `solid.ani`"); data.put("passiveobject/solid.ani", ani("green.img", 0, -4, 1));
        var frame = scene(data, "[passive object]\n1 20 30 0\n2 40 30 0\n999 60 30 0\n[/passive object]").render(0, false);
        assertEquals(3, frame.entities().size()); assertFalse(frame.entities().get(0).problem().isEmpty());
        assertTrue(frame.entities().get(1).problem().isEmpty()); assertTrue(frame.entities().get(2).problem().contains("999"));
        var image = paint(frame); assertEquals(new Color(255, 185, 65).getRGB(), image.getRGB(20, 150));
        assertEquals(Color.GREEN.getRGB(), image.getRGB(40, 147));
    }
    @Test void idleCyclesAndUnsavedDefinitionSnapshotsUseExistingSceneTime() throws Exception {
        Map<String, String> data = base(); data.put("npc/npc.lst", "1 `a.npc`"); data.put("npc/a.npc", "[field animation] `a.ani`");
        data.put("npc/a.ani", "[LOOP] 1 [FRAME000] [IMAGE] `green.img` 0 [DELAY] 100 [FRAME001] [IMAGE] `red.img` 0 [DELAY] 200");
        String placements = "[NPC]\n1 `[right]` 20 30 0\n[/NPC]";
        var scene = scene(data, placements); assertEquals(Color.RED.getRGB(), paint(scene.render(150, false)).getRGB(20, 150));
        assertEquals(Color.GREEN.getRGB(), paint(scene.render(300, false)).getRGB(20, 150));
        data.put("npc/a.npc", "[field animation] `b.ani`"); data.put("npc/b.ani", ani("blue.img", 0, 0, 1));
        assertEquals(Color.BLUE.getRGB(), paint(scene(data, placements).render(150, false)).getRGB(20, 150));
    }
    private static Map<String, String> base() {
        Map<String, String> map = new HashMap<>(); map.put("map/a.til", "[IMAGE] `` 0 [pass type] " + "0 ".repeat(420)); return map;
    }
    private static ScenePreview scene(Map<String, String> data, String placements) throws IOException {
        return ScenePreview.load("map/test.map", "[tile] `a.til` [/tile]\n" + placements, data::get, new Images());
    }
    private static String ani(String image, int x, int y, int loop) {
        return "[LOOP] " + loop + " [FRAME000] [IMAGE] `" + image + "` 0 [IMAGE POS] " + x + " " + y + " [DELAY] 100";
    }
    private static BufferedImage paint(ScenePreview.Rendered frame) {
        BufferedImage image = new BufferedImage(224, 480, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        try { frame.paint(g, new Rectangle(0, 0, 224, 480), 0, 0, 1, 0, 0, false); } finally { g.dispose(); } return image;
    }
    private static class Images implements ScenePreview.SpriteLookup {
        @Override public NpkResources.Info info(String path, int frame) { return new NpkResources.Info(4, 4, 0, 0, 4, 4); }
        @Override public NpkResources.Sprite sprite(String path, int frame) {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
            g.setColor(path.equals("red.img") ? Color.RED : path.equals("blue.img") ? Color.BLUE : path.equals("yellow.img") ? Color.YELLOW : Color.GREEN);
            g.fillRect(0, 0, 4, 4); if (path.equals("pattern.img")) { g.setColor(Color.RED); g.fillRect(0, 0, 1, 4); }
            g.dispose(); return new NpkResources.Sprite(image, 0, 0, 4, 4, Path.of("test.npk"), path, frame, frame);
        }
    }
}
