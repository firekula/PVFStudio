package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.FrameOverride;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** Priest state 16 with the Charge Weapon passive. */
final class Act1PriestAntiairUpper {
    private static final String PATH = "skill/priest/antiairupper.skl";
    private static final String CHARGE_SKILL = "skill/priest/chargeweapon.skl";
    private static final String CHARGED = "antiairupper:charged";
    private static final String ACTION = "etc motion:1";
    private static final String WAIT = "waiting motion";
    private static final String CHARGE = "character/priest/effect/animation/chargeweapon.ani";
    private static final String OBJECTS = "passiveobject/character/priest/";
    private static final int[] QUAKE_X = {-153, -114, -163, -170, -186};

    private Act1PriestAntiairUpper() {}

    static List<GameEffectRecipes.Effect> choices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1 || !path.equals(PATH) || !bindings(lookup)) return List.of();
        return List.of(new GameEffectRecipes.Effect(CHARGED, "空斩打 · 潜龙满蓄力", "", path,
                "已学习潜龙，按住技能键蓄力，演示上挑刀光和下砸震波各自的命中范围"));
    }

    static Act1SkillSequence find(String path, GameScript.Lookup lookup, Map<String, String> actions,
                                  String weaponPath, String choice) throws IOException {
        if (!path.equals(PATH) || !choice.equals(CHARGED) || !bindings(lookup)) return null;
        var frames = Act1SkillSequence.frames("character/priest/priest.chr", actions.get(ACTION), lookup);
        if (frames.size() < 6 || !actions.containsKey(WAIT)) return null;
        var chargeSkill = GameScript.fields(lookup.read(CHARGE_SKILL));
        int hold = Act1SkillSequence.staticValue(chargeSkill, 4, 0);
        int radius = Act1SkillSequence.staticValue(chargeSkill, 8, 0);
        if (hold < 1 || hold > 5000 || radius < 1 || radius > 1000) return null;

        long chargeAt = Act1SkillSequence.duration(frames, 2, null);
        long release = chargeAt + hold;
        long bladeAt = Act1SkillSequence.duration(frames, 3, null) + hold;
        long quakeAt = Act1SkillSequence.duration(frames, 5, null) + hold;
        long recovery = Act1SkillSequence.duration(frames, frames.size(), null) + hold;
        String blade = motion(lookup, "antiairupperblade");
        String quake = motion(lookup, "antiairupperquake");
        if (blade == null || quake == null) return null;
        long bladeEnd = bladeAt + duration(lookup, blade);
        long quakeEnd = quakeAt + duration(lookup, quake);
        if (bladeEnd <= bladeAt || quakeEnd <= quakeAt) return null;

        var weapon = GameScript.fields(weaponPath == null || weaponPath.isEmpty() ? null : lookup.read(weaponPath));
        int subtype = GameScript.integer(GameScript.value(weapon, "sub type"), 0);
        int quakeX = QUAKE_X[subtype >= 0 && subtype < QUAKE_X.length ? subtype : 0];
        long end = Math.max(recovery, Math.max(bladeEnd, quakeEnd));
        var poses = List.of(new Pose(ACTION, 0, chargeAt, null, false, null, 0, 2),
                new Pose(ACTION, chargeAt, release, hold, false, null, 1, 2),
                new Pose(ACTION, release, recovery, null, false, null, 2, frames.size()),
                new Pose(WAIT, recovery, end));
        var visuals = new ArrayList<Visual>();
        String chargeEffect = lookup.read(CHARGE);
        if (chargeEffect != null && ResourceScript.animation(chargeEffect).frames().size() == 12) {
            var overrides = new HashMap<Integer, FrameOverride>();
            for (int i = 0; i < 12; i++) overrides.put(i, new FrameOverride(0, 0, hold / 12));
            var style = new VisualStyle(null, 0, List.of(), overrides, false);
            visuals.add(new Visual(CHARGE, "空斩打 · 潜龙蓄力", 3502, chargeAt, release, 24, -67, null, 1, true, style));
        }
        visuals.add(new Visual(blade, "空斩打 · 满蓄上挑", 3501, bladeAt, bladeEnd, 0, 0));
        visuals.add(new Visual(quake, "空斩打 · 满蓄下砸震波", 280, quakeAt, quakeEnd, quakeX, 0, null, radius / 298.0, true));
        addTarget(visuals, lookup, "刀光", 105, bladeAt, end);
        addTarget(visuals, lookup, "震波", quakeX, quakeAt, end);
        return new Act1SkillSequence(poses, List.copyOf(visuals), List.of(),
                "潜龙在上挑第 4 帧发出刀光，在下砸第 6 帧生成地面震波，地面落点随武器变化。前后各一只哥布林演示两段的攻击范围");
    }

    private static boolean bindings(GameScript.Lookup lookup) throws IOException {
        var skills = GameScript.catalog(lookup.read("skill/priestskill.lst"));
        var objects = GameScript.catalog(lookup.read("passiveobject/passiveobject.lst"));
        return PATH.equals("skill/" + skills.get(1)) && CHARGE_SKILL.equals("skill/" + skills.get(66))
                && lookup.read(CHARGE_SKILL) != null
                && "character/priest/antiairupperblade.obj".equals(objects.get(24017))
                && "character/priest/antiairupperquake.obj".equals(objects.get(24018));
    }

    private static String motion(GameScript.Lookup lookup, String stem) throws IOException {
        String path = OBJECTS + stem + ".obj";
        return GameScript.resolve(path, GameScript.value(GameScript.fields(lookup.read(path)), "basic motion"), lookup);
    }

    private static long duration(GameScript.Lookup lookup, String path) throws IOException {
        return new AnimationTimeline(ResourceScript.animation(lookup.read(path))).duration();
    }

    private static void addTarget(List<Visual> visuals, GameScript.Lookup lookup, String label,
                                  double x, long hit, long end) throws IOException {
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "空斩打 · " + label + "目标", 0, hit, x, null);
        var launch = new SkillMotion(hit, 600, 0, 0, 300, -1000);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "overturn", "空斩打 · " + label + "击飞", hit, end, x, launch);
    }
}
