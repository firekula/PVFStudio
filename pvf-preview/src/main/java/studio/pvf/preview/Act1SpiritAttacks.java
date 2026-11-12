package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1MageSupplement.SummonAttackResult;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** State 8 attacks and ally support for ACT1 Mage's original spirits. */
final class Act1SpiritAttacks {
    private static final int NAIAS = 60006;
    private static final int STALKER = 60008;
    private static final int WISP = 60009;
    private static final int ECHEVERRA = 904;
    private static final int BODY_LAYER = 3550;
    private static final String ATTACK = "attack motion";
    private static final String EXTRA = "etc motion";
    private static final String AURA = "monster/spikeking/animation_spikeking/aura.ani";
    private static final String ADOR_IDLE = "monster/spirit/fireanimation/stay.ani";
    private static final String NAIAS_IDLE = "monster/spirit/wateranimation/stay.ani";
    private static final String ICE_OBJECT = "passiveobject/monster/spirit/summonmonstericewaveup.obj";

    private enum Move {
        ICE_PILLAR(NAIAS, "ice-pillar", "冰奈斯 · 冰柱", "冰奈斯施法，在远处目标脚下生成冰柱。"),
        DARK_ATTACK(STALKER, "dark-attack", "瑟冥特克 · 二式",
                "演示客户端攻击状态的第二套动作与接触判定。原版召唤物的 AI 攻击表未启用此式。"),
        LIGHTNING(WISP, "lightning", "雷沃斯 · 雷击",
                "演示客户端攻击状态的落雷与放电两段判定。原版召唤物的 AI 攻击表未启用此式。"),
        KING_AURA(ECHEVERRA, "king-aura", "伊伽贝拉 · 强化光环", "场上有友军精灵时施放强化光环。"),
        KING_CONSUME(ECHEVERRA, "king-consume", "伊伽贝拉 · 吞噬回血", "低血量时吞噬 100 范围内最近的一只友军精灵。");

        final int monsterId;
        final String key;
        final String label;
        final String description;

        Move(int monsterId, String key, String label, String description) {
            this.monsterId = monsterId;
            this.key = "mage-spirit:" + key;
            this.label = label;
            this.description = description;
        }
    }

    private Act1SpiritAttacks() {}

    static List<GameEffectRecipes.Effect> choices(String skill, int monsterId, String monster, GameScript.Lookup lookup)
            throws IOException {
        var choices = new ArrayList<GameEffectRecipes.Effect>();
        for (var move : Move.values()) {
            if (move.monsterId != monsterId) continue;
            if (append(monsterId, monster, move.key, lookup, 0, 100, new ArrayList<>()) != null) {
                choices.add(new GameEffectRecipes.Effect(move.key, move.label, "", skill, move.description));
            }
        }
        return List.copyOf(choices);
    }

    static SummonAttackResult append(int monsterId, String monster, String choice, GameScript.Lookup lookup,
                                     long start, double x, List<Visual> visuals) throws IOException {
        if (monster == null) return null;
        if (choice.isEmpty()) {
            return switch (monsterId) {
                case WISP -> contact(monsterId, monster, 0, -1, lookup, start, x, "雷沃斯 · 普通攻击", visuals);
                case ECHEVERRA -> contact(monsterId, monster, 0, 0, lookup, start, x, "伊伽贝拉 · 手臂挥击", visuals);
                default -> null;
            };
        }
        for (var move : Move.values()) {
            if (move.monsterId != monsterId || !move.key.equals(choice)) continue;
            return switch (move) {
                case ICE_PILLAR -> icePillar(monster, lookup, start, x, move.label, visuals);
                case DARK_ATTACK -> contact(monsterId, monster, 1, -1, lookup, start, x, move.label, visuals);
                case LIGHTNING -> contact(monsterId, monster, 1, 3, lookup, start, x, move.label, visuals);
                case KING_AURA -> aura(monster, lookup, start, x, move.label, visuals);
                case KING_CONSUME -> consume(monster, lookup, start, x, move.label, visuals);
            };
        }
        return null;
    }

    /** 0x6B90F0 and 0x6B7F40 spawn object 30380 at the chosen target on casting frame 1. */
    private static SummonAttackResult icePillar(String monster, GameScript.Lookup lookup, long start, double x,
                                               String label, List<Visual> visuals) throws IOException {
        var body = motion(reference(monster, ATTACK, 1, lookup), lookup);
        if (body == null || body.animation.frames().size() < 2
                || !ICE_OBJECT.equals(GameEffectRecipes.passiveObject(lookup, 30380))) return null;
        var pillar = motion(reference(ICE_OBJECT, "basic motion", 0, lookup), lookup);
        if (pillar == null || !pillar.attack.hasAttackBoxes()
                || reference(ICE_OBJECT, "attack info", 0, lookup) == null) return null;

        double targetX = x + 180;
        long bodyEnd = start + body.duration();
        long spawn = start + Math.max(0, body.animation.frames().get(0).delay());
        long end = Math.max(bodyEnd, spawn + pillar.duration());
        visuals.add(new Visual(body.path, label + " · 施法", BODY_LAYER, start, bodyEnd, x, 0).withoutAttack());
        visuals.add(new Visual(pillar.path, label, 3600, spawn, spawn + pillar.duration(), targetX, 0).asAlliedAttack());
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", label + "目标 · 哥布林", start, end, targetX, null);
        return new SummonAttackResult(bodyEnd, end, x);
    }

    /** 0x6BB5F0 selects spirit attack motions, 0x6BBEC0 attaches the Wisp's lightning, 0x68EC10 attaches the King's arm. */
    private static SummonAttackResult contact(int monsterId, String monster, int index, int effectIndex,
                                             GameScript.Lookup lookup, long start, double x, String label,
                                             List<Visual> visuals) throws IOException {
        var body = motion(reference(monster, ATTACK, index, lookup), lookup);
        if (body == null || !body.attack.hasAttackBoxes()
                || reference(monster, "attack info", index, lookup) == null) return null;
        var effect = effectIndex < 0 ? null : motion(reference(monster, EXTRA, effectIndex, lookup), lookup);
        if (effectIndex >= 0 && effect == null) return null;
        var targetBox = body.animation.frames().stream().flatMap(frame -> frame.combat().attack().stream())
                .findFirst().orElseThrow();

        long bodyEnd = start + body.duration();
        visuals.add(new Visual(body.path, label, BODY_LAYER, start, bodyEnd, x, 0).asAlliedAttack());
        equipment(monsterId, ATTACK, index, label, lookup, start, bodyEnd, x, VisualStyle.DEFAULT, visuals);
        long end = addEffect(effect, label + " · 特效", BODY_LAYER + 100, start, x, visuals);
        end = Math.max(end, bodyEnd);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", label + "目标 · 哥布林", start, end,
                x + targetBox.x() + targetBox.width() / 2, null);
        return new SummonAttackResult(bodyEnd, end, x);
    }

    /** 0x68EC10 index 1 creates a 300-unit allied-spirit aura at state entry. */
    private static SummonAttackResult aura(String monster, GameScript.Lookup lookup, long start, double x,
                                          String label, List<Visual> visuals) throws IOException {
        var body = motion(reference(monster, ATTACK, 1, lookup), lookup);
        var aura = motion(AURA, lookup);
        var ador = motion(ADOR_IDLE, lookup);
        var naias = motion(NAIAS_IDLE, lookup);
        if (body == null || aura == null || ador == null || naias == null) return null;

        long bodyEnd = start + body.duration();
        long end = bodyEnd + 1000;
        visuals.add(new Visual(body.path, label + " · 施法", BODY_LAYER, start, bodyEnd, x, 0).withoutAttack());
        equipment(ECHEVERRA, ATTACK, 1, label, lookup, start, bodyEnd, x, VisualStyle.DEFAULT, visuals);
        visuals.add(new Visual(aura.path, label, BODY_LAYER - 100, start, end, x, 0).withoutAttack());
        addIdle(visuals, ador, "光环内 · 亚德炎", start, end, x + 75);
        addIdle(visuals, naias, "光环内 · 冰奈斯", start, end, x + 150);
        return new SummonAttackResult(bodyEnd, end, x);
    }

    /** 0x68EC10 index 2 attaches separate effects to the King and one nearby ally, which enters state 21. */
    private static SummonAttackResult consume(String monster, GameScript.Lookup lookup, long start, double x,
                                             String label, List<Visual> visuals) throws IOException {
        var body = motion(reference(monster, ATTACK, 2, lookup), lookup);
        var kingEffect = motion(reference(monster, EXTRA, 1, lookup), lookup);
        var spiritEffect = motion(reference(monster, EXTRA, 2, lookup), lookup);
        var ally = motion(ADOR_IDLE, lookup);
        if (body == null || kingEffect == null || spiritEffect == null || ally == null) return null;

        long bodyEnd = start + body.duration();
        double allyX = x + 70;
        visuals.add(new Visual(body.path, label + " · 施法", BODY_LAYER, start, bodyEnd, x, 0).withoutAttack());
        equipment(ECHEVERRA, ATTACK, 2, label, lookup, start, bodyEnd, x, VisualStyle.DEFAULT, visuals);
        addIdle(visuals, ally, "被吞噬的亚德炎", start, start + 1500, allyX);
        // 0x6B89F0 and 0x82E040 draw the ally normally, then overlay a rising white mask before removal.
        var maskFrames = ally.animation.frames().stream().map(frame -> new ResourceScript.Frame(frame.image(),
                frame.index(), frame.x(), frame.y(), frame.scaleX(), frame.scaleY(), frame.rotation(),
                255, 255, 255, 255, frame.delay(), frame.line(), PreviewRenderer.WHITE_MASK, frame.interpolation()))
                .toList();
        var mask = new ResourceScript.Animation(ally.animation.loop(), maskFrames, List.of());
        var fade = new VisualStyle(null, 0, List.of(new VisualKeyframe(0, 1, 0), new VisualKeyframe(1500, 1, 1)),
                Map.of(), false, ally.duration(), mask);
        visuals.add(new Visual(ally.path, "吞噬白光 · 亚德炎", BODY_LAYER + 1, start, start + 1500, allyX, 0,
                null, 1, true, fade).withoutAttack());
        long end = addEffect(kingEffect, label, BODY_LAYER + 100, start, x, visuals);
        end = Math.max(end, addEffect(spiritEffect, label + " · 精灵", BODY_LAYER + 100, start, allyX, visuals));
        return new SummonAttackResult(bodyEnd, Math.max(end, bodyEnd), x);
    }

    private static void addIdle(List<Visual> visuals, Motion idle, String label, long start, long end, double x) {
        var loop = new VisualStyle(null, 0, List.of(), Map.of(), false, idle.duration());
        visuals.add(new Visual(idle.path, label, BODY_LAYER, start, end, x, 0, null, 1, true, loop).withoutAttack());
    }

    static void appendIdleEquipment(int monsterId, String label, GameScript.Lookup lookup, long start, long end,
                                    double x, VisualStyle style, List<Visual> visuals) throws IOException {
        equipment(monsterId, "waiting motion", 0, label, lookup, start, end, x, style, visuals);
    }

    private static void equipment(int monsterId, String tag, int index, String label, GameScript.Lookup lookup,
                                  long start, long end, double x, VisualStyle style, List<Visual> visuals) throws IOException {
        if (start >= end) return;
        // 0x6BBF80 loads the Wisp glow. 0x68F6D0 equips the King in acc, clothes, glow, hair order.
        var equipment = switch (monsterId) {
            case WISP -> List.of("equipment/monster/spirit/light1glow.equ");
            case ECHEVERRA -> List.of("equipment/monster/spikeking/spikeking_acc.equ",
                    "equipment/monster/spikeking/spikeking_clothes.equ", "equipment/monster/spikeking/spikeking_glow.equ",
                    "equipment/monster/spikeking/spikeking_hair.equ");
            default -> List.<String>of();
        };
        for (String path : equipment) {
            var fields = GameScript.fields(lookup.read(path));
            String animation = reference(path, fields, tag, index, lookup);
            if (animation == null) continue;
            int layer = GameScript.integer(GameScript.value(fields, "layer"), 0);
            visuals.add(new Visual(animation, label, BODY_LAYER + Math.max(-1000, Math.min(1000, layer)),
                    start, end, x, 0, null, 1, true, style).withoutAttack());
        }
    }

    private static long addEffect(Motion effect, String label, int layer, long start, double x, List<Visual> visuals) {
        if (effect == null) return start;
        long end = start + effect.duration();
        visuals.add(new Visual(effect.path, label, layer, start, end, x, 0).withoutAttack());
        return end;
    }

    private static String reference(String owner, String tag, int index, GameScript.Lookup lookup) throws IOException {
        return reference(owner, GameScript.fields(lookup.read(owner)), tag, index, lookup);
    }

    private static String reference(String owner, List<GameScript.Field> fields, String tag, int index,
                                    GameScript.Lookup lookup) throws IOException {
        var references = GameScript.values(fields, tag);
        return index < references.size() && !references.get(index).isEmpty()
                ? GameScript.resolve(owner, references.get(index), lookup) : null;
    }

    private static Motion motion(String path, GameScript.Lookup lookup) throws IOException {
        if (path == null) return null;
        var animation = ResourceScript.animation(lookup.read(path));
        var timeline = new AnimationTimeline(animation);
        return timeline.duration() > 0 ? new Motion(path, animation, timeline) : null;
    }

    private record Motion(String path, ResourceScript.Animation animation, AnimationTimeline attack) {
        long duration() { return attack.duration(); }
    }
}
