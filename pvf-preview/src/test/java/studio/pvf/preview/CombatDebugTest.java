package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombatDebugTest {
    @Test void clickPicksTheFrontBoxAndRepeatedClicksReachOverlappingBoxes() {
        var box = new CombatFrame.Box(0,0,0,30,20,40);
        var volumes = List.of(
                new CombatDebug.Volume(CombatDebug.Kind.PLAYER_HURT,box,0,0,false,"body"),
                new CombatDebug.Volume(CombatDebug.Kind.ATTACK,box,0,0,false,"swing"));
        assertEquals(1,CombatDebug.pick(volumes,100,200,2,150,190,-1));
        assertEquals(0,CombatDebug.pick(volumes,100,200,2,150,190,1));
        assertEquals(1,CombatDebug.pick(volumes,100,200,2,150,190,0));
        assertEquals(-1,CombatDebug.pick(volumes,100,200,2,70,190,1));
    }

    @Test void projectionMatchesTheClientAndOnlyMirrorsTheWorldXInterval() {
        var box = new CombatFrame.Box(-10,-5,0,25,10,100);
        var right = new CombatDebug.Volume(CombatDebug.Kind.PLAYER_HURT,box,13,-11,false,"body");
        var left = new CombatDebug.Volume(CombatDebug.Kind.PLAYER_HURT,box,-13,-11,true,"body");
        var r = CombatDebug.corners(right,100,200,2);
        var l = CombatDebug.corners(left,100,200,2);
        assertEquals(50,r[1].x-r[0].x,.001);
        assertEquals(0,r[2].x-r[0].x,.001);
        assertEquals(20,r[2].y-r[0].y,.001);
        assertEquals(-200,r[4].y-r[0].y,.001);
        assertEquals(-15,left.orientedBox().x());
        for(int i=0;i<8;i++) assertEquals(r[i].y,l[i].y,.001);
        assertTrue(left.description().contains("X -15  Y -5  Z 0"));
        assertEquals(106,r[0].x,.001);
        assertEquals(168,r[0].y,.001);
        assertEquals(-32,r[4].y,.001);
        for(int i=0;i<8;i++) assertEquals(200,r[i].x+l[i^1].x,.001);
    }
    @Test void targetRangeUsesTheClientDepthAndHeightAndCanBePickedAsAnEllipse() {
        var range = new CombatDebug.TargetRange(100,70,1000);
        assertTrue(range.contains(0,29,70));
        assertFalse(range.contains(0,30,70));
        assertFalse(range.contains(0,29,71));
        assertFalse(range.contains(100,29,0));
        var area = new CombatDebug.Volume(CombatDebug.Kind.ATTACK,range.envelope(),122,0,false,"range",range,false);
        assertEquals(0,CombatDebug.pick(List.of(area),0,0,1,122,-70,-1));
        assertEquals(-1,CombatDebug.pick(List.of(area),0,0,1,30,-94,-1));
    }
}
