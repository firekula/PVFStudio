package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;

/** Collision-triggered resources for the ACT1 Fighter, illustrated with one stock target. */
final class Act1FighterHits {
    private static final String EFFECT = "character/fighter/effect/animation/";
    private static final String CHR = "character/fighter/fighter.chr";
    private static final String MISS = "fighter-hit:miss";
    private static final String COUNTER = "fighter-hit:counter";
    private static final int FRONT = 3501;

    private enum Hit {
        CLOSE_PUNCH("closepunch", 19, "寸拳"),
        BRICK("blockbuster", 60, "砖袭"),
        LOW_KICK("strongestlowkick", 71, "武神强踢"),
        STING("hiddensting", 75, "毒影针"),
        TACKLE("shouldertackle", 54, "野蛮冲撞"),
        DANCE("lightningdance", 68, "闪电之舞");

        final String path;
        final int id;
        final String label;

        Hit(String stem, int id, String label) {
            this.path = "skill/fighter/" + stem + ".skl";
            this.id = id;
            this.label = label;
        }
    }

    private Act1FighterHits() {}

    private static Hit skill(String path) {
        for (var hit : Hit.values()) {
            if (hit.path.equals(path)) return hit;
        }
        return null;
    }

    static boolean miss(String path, String choice) {
        return MISS.equals(choice) && skill(path) != null;
    }

    static List<GameEffectRecipes.Effect> choices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        var hit = act1 ? skill(path) : null;
        if (hit == null || !path.equals("skill/" + GameScript.catalog(lookup.read("skill/fighterskill.lst")).get(hit.id)))
            return List.of();
        var miss = new GameEffectRecipes.Effect(MISS, hit.label + " · 未命中", "", path, "播放未接触目标的施放");
        if (hit != Hit.LOW_KICK) return List.of(miss);
        return List.of(miss, new GameEffectRecipes.Effect(COUNTER, hit.label + " · 破招命中", "", path,
                "用正在攻击的小怪展示普通站立角色达成两个条件的命中特效"));
    }

    static Act1SkillSequence find(String path, GameScript.Lookup lookup, Map<String, String> actions, String choice)
            throws IOException {
        var hit = skill(path);
        if (hit == null || hit == Hit.TACKLE || hit == Hit.DANCE) return null;
        if (!choice.isEmpty() && !choice.equals(MISS) && !(hit == Hit.LOW_KICK && choice.equals(COUNTER))) return null;
        if (hit == Hit.CLOSE_PUNCH) return closePunch(lookup, actions, choice.equals(MISS));
        var attached = GameEffectRecipes.fighterCast(path, lookup, actions, true);
        if (attached == null) return null;
        String body = GameScript.resolve(CHR, actions.get(attached.action()), lookup);
        if (body == null) return null;
        String source = lookup.read(body);
        long recovery = duration(source);
        if (recovery <= 0) return null;
        // Forward contact is selected within the authored collision boxes, so the raised hand behind the
        // caster in Brick and Sting cannot trigger their effects in this scenario.
        double targetX = hit == Hit.LOW_KICK ? 58 : 51;
        long contact = firstForwardAttack(source, targetX);
        boolean landed = !choice.equals(MISS) && contact >= 0;
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        poses.add(new Pose(attached.action(), 0, recovery));
        for (var clip : attached.clips()) {
            long end = clip.end() >= 0 ? clip.end() : recovery;
            if (end > clip.start() && lookup.read(clip.path()) != null)
                visuals.add(new Visual(clip.path(), clip.label(), clip.renderLayer(), clip.start(), end, clip.x(), clip.y()));
        }
        long end = recovery;
        if (landed) {
            end = switch (hit) {
                case BRICK -> brick(lookup, visuals, emitters, targetX, contact, recovery);
                case STING -> Math.max(recovery, add(lookup, visuals, EFFECT + "hiddenstingdrop.ani",
                        "毒影针 · 命中落针", FRONT + 1, contact, 0, 0));
                case LOW_KICK -> Math.max(recovery, add(lookup, visuals, EFFECT + "strongestlowkick/"
                                + (choice.equals(COUNTER) ? "hit3.ani" : "hit2.ani"),
                        "武神强踢 · 命中光效", FRONT + 101, contact, targetX, -30));
                default -> recovery;
            };
            addTarget(lookup, visuals, hit.label, targetX, contact, Math.max(end, contact + 240),
                    choice.equals(COUNTER) ? "attack1" : "stay");
        }
        end = Math.max(end, contact + (landed ? 240 : 0));
        if (actions.containsKey("waiting motion") && end > recovery) poses.add(new Pose("waiting motion", recovery, end));
        String note = landed ? hit.label + "按命中分支播放，碰撞位置用一只普通哥布林演示"
                : hit.label + "播放单人施放和收招";
        if (landed && hit == Hit.LOW_KICK && choice.equals(COUNTER))
            note += "，破招使用第三档命中光效，预览相机固定";
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters), note);
    }

    /** State 21 attaches Execute on entry; 0x5E0FD0 creates #21009 only after contact. */
    private static Act1SkillSequence closePunch(GameScript.Lookup lookup, Map<String, String> actions, boolean miss)
            throws IOException {
        if (!Act1SkillSequence.matches("skill/fighter/closepunch.skl", "fighter", 19, lookup)) return null;
        String action = "etc motion:13";
        String body = GameScript.resolve(CHR, actions.get(action), lookup);
        String execute = EFFECT + "closepunchexecute.ani";
        String source = lookup.read(execute);
        if (body == null || source == null) return null;
        long recovery = duration(lookup.read(body));
        if (recovery <= 0) return null;
        var visuals = new ArrayList<Visual>();
        add(lookup, visuals, execute, "寸拳 · 出拳光效", FRONT, 0, 33, -49);
        long end = recovery;
        var shock = Act1SkillSequence.objectMotions(lookup, 21009);
        if (!miss && !shock.isEmpty()) {
            var frames = ResourceScript.animation(source).frames();
            // The demonstration places a target in front of the first visible punch frame.
            long contact = Act1SkillSequence.duration(frames, 1, null);
            end = Math.max(end, add(lookup, visuals, shock.get(0), "寸拳 · 命中冲击波", FRONT + 100, contact, 36, -49));
            addTarget(lookup, visuals, "寸拳", 36, contact, end, "stay");
        }
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(action, 0, recovery));
        if (actions.containsKey("waiting motion") && end > recovery) poses.add(new Pose("waiting motion", recovery, end));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(), miss
                ? "寸拳播放未命中的出拳光效和收招"
                : "寸拳以贴身哥布林演示接触后的独立冲击波");
    }

    /** #21013 reads collision XYZ, then creates dust and a particle burst on frame 1 (0x599410). */
    private static long brick(GameScript.Lookup lookup, List<Visual> visuals, List<SkillParticleEmitter> emitters,
                              double x, long contact, long recovery) throws IOException {
        String object = GameEffectRecipes.passiveObject(lookup, 21013);
        var motions = Act1SkillSequence.objectMotions(lookup, 21013);
        if (object == null || motions.isEmpty()) return recovery;
        String source = lookup.read(motions.get(0));
        long burst = contact + Act1SkillSequence.duration(ResourceScript.animation(source).frames(), 1, null);
        String dust = GameScript.resolve(object, "Animation/BlockBusterDust.ani", lookup);
        String ptl = GameScript.resolve(object, "Particle/BlockBusterPiece.ptl", lookup);
        String glow = GameScript.resolve(object, "Animation/BlockBusterPieceGlow.ani", lookup);
        double z = 58;
        long end = Math.max(recovery, add(lookup, visuals, dust, "砖袭 · 碎砖扬尘", FRONT + 100, burst, x, -z + .29));
        end = Math.max(end, add(lookup, visuals, motions.get(0), "砖袭 · 碎砖判定", FRONT + 100, contact, x, -z));
        if (ptl != null) {
            emitters.add(new SkillParticleEmitter(ptl, "砖袭 · 碎片", burst, 1, 0, x, 0, z,
                    Double.NaN, 1, true));
            if (glow != null)
                emitters.add(new SkillParticleEmitter(ptl, "砖袭 · 碎片光效", burst, 1, 0, x, 0, z,
                        Double.NaN, 1, true, 1, false, null, glow));
            end = Math.max(end, burst + 1 + SkillParticleEmitter.tail(ParticleScript.parse(lookup.read(ptl))));
        }
        return end;
    }

    static long firstForwardAttack(String source, double x) throws IOException {
        if (source == null) return -1;
        var frames = ResourceScript.animation(source).frames();
        int frame = -1;
        for (var section : ResourceScript.sections(source)) {
            if (section.tag().matches("frame[0-9]+")) frame++;
            if (frame < 0 || frame >= frames.size() || !section.tag().equals("attack box") || section.values().size() < 6) continue;
            int left = GameScript.integer(section.values().get(0), 0);
            int width = GameScript.integer(section.values().get(3), 0);
            if (left <= x && x <= left + width) return Act1SkillSequence.duration(frames, frame, null);
        }
        return -1;
    }

    static void addTarget(GameScript.Lookup lookup, List<Visual> visuals, String label, double x, long hit, long end,
                          String before) throws IOException {
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, before, label + " · 目标", 0, hit, x, null);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", label + " · 目标受击", hit, end, x, null);
    }

    private static long add(GameScript.Lookup lookup, List<Visual> visuals, String path, String label, int layer,
                            long start, double x, double y) throws IOException {
        if (path == null) return start;
        long end = start + duration(lookup.read(path));
        if (end > start) visuals.add(new Visual(path, label, layer, start, end, x, y));
        return end;
    }

    private static long duration(String source) throws IOException {
        return source == null ? 0 : new AnimationTimeline(ResourceScript.animation(source)).duration();
    }
}
