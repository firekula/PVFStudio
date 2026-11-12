package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1PreviewWeaponTest {
    @Test void weaponBranchUsesTheLowestMatchingEquipAndDoesNotChangeSavedOutfit() throws Exception {
        String skill="skill/swordman/weaponcombo.skl";
        String root="equipment/character/swordman/weapon/";
        String shortSword=root+"ssword/current.equ",lowest=root+"katana/z.equ";
        var data=Map.of(shortSword,weapon(0,12,0,"swordman"),lowest,weapon(1,2,0,"swordman"),
                root+"katana/a.equ",weapon(1,10,0,"swordman"),root+"katana/foreign.equ",weapon(1,1,0,"mage"),
                root+"katana/rare.equ",weapon(1,2,4,"swordman"));
        var saved=Map.of("weapon",shortSword,"coat avatar","equipment/coat.equ");
        var selected=Act1PreviewWeapon.outfit(skill,"swordman","weapon:1",true,data.keySet(),data::get,saved);
        assertEquals(lowest,selected.get("weapon"));
        assertEquals(saved.get("coat avatar"),selected.get("coat avatar"));
        assertEquals(shortSword,saved.get("weapon"));
        assertEquals("",Act1PreviewWeapon.recipeChoice(skill,"swordman","weapon:1",true));
        assertEquals(saved,Act1PreviewWeapon.outfit(skill,"swordman","",true,data.keySet(),data::get,saved));
        assertEquals(lowest,Act1PreviewWeapon.outfit(Act1SwordTechniqueCast.MOMENTARY_PATH,"swordman",
                Act1SwordTechniqueCast.KATANA,true,data.keySet(),data::get,saved).get("weapon"));
    }

    @Test void automaticWeaponAppliesToSkillsAndRespectsExplicitHide() throws Exception {
        String staff="equipment/character/mage/weapon/spear/low.equ";
        var data=Map.of(staff,weapon(0,2,0,"mage"));
        String skill="skill/mage/magicmissile.skl";
        assertEquals(staff,Act1PreviewWeapon.outfit(skill,"mage","",true,data.keySet(),data::get,Map.of()).get("weapon"));
        var hidden=Map.of("weapon","");
        assertEquals(hidden,Act1PreviewWeapon.outfit(skill,"mage","",true,data.keySet(),data::get,hidden));
        assertTrue(Act1PreviewWeapon.outfit("equipment/mage/coat.equ","mage","",true,
                data.keySet(),data::get,Map.of()).isEmpty());
    }

    private static String weapon(int type,int level,int rarity,String job){
        return "[equipment type] `[weapon]` [sub type] "+type+" [minimum level] "+level+
                " [rarity] "+rarity+" [usable job] `["+job+"]`";
    }
}
