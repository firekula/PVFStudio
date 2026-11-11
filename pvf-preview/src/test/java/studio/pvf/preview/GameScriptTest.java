package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GameScriptTest {
    @Test void gameTooltipPairsCastWithMpAndUsesSecondsAndOneDescription() throws Exception {
        var info=GameItemInfo.read("skill/gunner/test.skl","""
                [name] `激光炮`
                [name2] `Laser Rifle`
                [type] `[active]`
                [maximum level] 30
                [cool time] 4500 4500
                [consume mp] 40 40
                [explain] `向敌人发射激光。`
                [basic explain] `向敌人发射激光。`
                [command key explain] `→→ + Z`
                """,p->null,14);
        assertEquals("激光炮 Lv14",info.tooltip().heading());
        var lines=info.tooltip().lines();
        assertEquals("施放时间：瞬发",lines.get(0).left());
        assertEquals("MP 40",lines.get(0).right());
        assertEquals("冷却时间：4.5秒",lines.get(1).left());
        assertEquals("操作指令：→→ + Z",lines.get(2).left());
        assertEquals(1,lines.stream().filter(line->line.left().equals("向敌人发射激光。")).count());
    }

    @Test void skillTooltipUsesTheConfiguredCastTimeAtSelectedLevel() throws Exception {
        var info=GameItemInfo.read("skill/mage/wallofice.skl","""
                [type] `[active]`
                [maximum level] 5
                [casting time] 1000 400
                """,path->null,3);
        assertEquals("施放时间：0.7秒",info.tooltip().lines().get(0).left());
    }
    @Test void acyclicLocalizedFanoutCannotExpandIntoUnboundedHtml() throws Exception {
        var table=new StringBuilder("k6>abcdefghij\n");for(int i=5;i>=0;i--)table.append("k").append(i).append('>').append(("<0::k"+(i+1)+">").repeat(10)).append('\n');var warnings=new java.util.ArrayList<String>();
        var strings=new GameScript.Strings(p->p.equals("string.lst")?"0 `a.str`":table.toString(),warnings);var output=strings.resolve("<0::k0>");assertTrue(output.length()<=65536,"Expanded "+output.length()+" chars");assertTrue(warnings.stream().anyMatch(w->w.contains("超限")));
    }
    @Test void resolvesChineseStringLinksAndRetainsUnknownKeys() throws Exception {
        var data=Map.of("string.lst","12 `Skill/Skill.chn.str`", "skill/skill.chn.str","name>激光炮\nexplain>第一行\\n第二行\n");
        var info=GameItemInfo.read("skill/gunner/laser.skl","[name] <12::name> [explain] <12::explain> [basic explain] <12::missing>",data::get,1);
        assertEquals("激光炮",info.name()); assertTrue(info.explanation().contains("第一行\n第二行"));
        assertTrue(info.explanation().contains("<12::missing>")); assertFalse(info.warnings().isEmpty());
    }
    @Test void preservesConfigurationRangesWithoutInventingRuntimeQuality() throws Exception {
        var info=GameItemInfo.read("equipment/test.equ","[name] `测试` [rarity] 3 [equipment physical attack] +338 +281 [physical attack] +17 [minimum level] 50",p->null,1);
        assertTrue(info.rows().stream().anyMatch(r->r.label().equals("物理攻击力")&&r.value().contains("338")&&r.value().contains("281")));
        assertTrue(info.rows().stream().anyMatch(r->r.label().equals("力量")&&r.value().equals("+17")));
        assertTrue(info.rows().stream().noneMatch(r->r.label().contains("品级")||r.label().contains("强化")));
    }
    @Test void quotedTagsAndCommentsNeverCreateFields() {
        var fields=GameScript.fields("// [name] `bad`\n[name] `[coat avatar]` <include> `equipment/a.lay` [variation] 41 0");
        assertEquals(List.of("name","include","variation"),fields.stream().map(GameScript.Field::tag).toList());
        assertEquals("[coat avatar]",fields.get(0).values().get(0));
    }
}
