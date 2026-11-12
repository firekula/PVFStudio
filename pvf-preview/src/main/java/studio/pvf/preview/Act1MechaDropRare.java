package studio.pvf.preview;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

import static studio.pvf.preview.Act1RobotAttack.ROBOT;

/** ACT1 CNRX78 states for the four Mechadrop replacements (0x66DF80 / 0x66F7B0 / 0x670020). */
final class Act1MechaDropRare {
    static final String PATH = "skill/gunner/mechadrop.skl";
    private static final String EARTHQUAKE = Act1RobotAttack.QUAKE;
    private static final String RECOVERY_ANIMATION = "common/commoneffect/animation/";
    private static final String RECOVERY_PARTICLE = "common/commoneffect/particle/healparticle.ptl";
    private static final VisualStyle FACE_ALLY = new VisualStyle(null, 0, List.of(), Map.of(), true);

    private Act1MechaDropRare() {}

    enum Variant {
        FIRE("fire", "红色 RX-78", 60014, "firerx78", "firerx78", 0, "演示落地、引爆动作和火属性爆炸"),
        HEADING("heading", "头撞 RX-78", 60015, "headingrx78", "headingrx78", 0, "演示连续冲撞与最后的爆炸"),
        HP("hp", "HP 恢复 RX-78", 60016, "hphealrx78", "healrx78", 13, "演示为附近友方恢复 HP"),
        MP("mp", "MP 恢复 RX-78", 60017, "mphealrx78", "healrx78", 14, "演示为附近友方恢复 MP");

        final String key;
        final String label;
        final int monsterId;
        final String monster;
        final String art;
        final int recoveryColumn;
        final String description;

        Variant(String key, String label, int monsterId, String monster, String art, int recoveryColumn, String description) {
            this.key = "mechadrop:" + key;
            this.label = label;
            this.monsterId = monsterId;
            this.monster = monster;
            this.art = art;
            this.recoveryColumn = recoveryColumn;
            this.description = description;
        }

        boolean heals() {
            return recoveryColumn != 0;
        }
    }

    static List<GameEffectRecipes.Effect> choices() {
        return Arrays.stream(Variant.values()).map(variant -> new GameEffectRecipes.Effect(
                variant.key, "空投支援 · " + variant.label, "", PATH, variant.description)).toList();
    }

    static Variant variant(String choice) {
        for (var variant : Variant.values()) if (variant.key.equals(choice)) return variant;
        return null;
    }

    private record Motion(String path, long duration) {}

    record Scenario(Variant variant, Motion drop, Motion trail, Motion land, Motion stay, Motion attack,
                    Motion windup, Motion healBody, Motion healTarget, long recoveryParticleDelay,
                    long recoveryParticleTail, long quake, long attackDelay, long lungeDuration, long firstHitDelay,
                    int attackCount, int recoveryAmount, double blastScale, Act1RobotAttack.Blast blast) {

        String note(int rareCount) {
            return "空投支援 · 固定演示 " + rareCount + " 台" + variant.label + "替换普通投放位，落点取原始抖动范围的中值。"
                    + "落地后按怪物攻击间隔演示预设的近距离触发。"
                    + (variant.heals() ? "每台在 500 ms 内为演示友方恢复 " + recoveryAmount
                            + (variant == Variant.HP ? " HP" : " MP") + "。"
                            : "首台机器人的攻击使用同一只哥布林演示受击。");
        }

        long add(GameScript.Lookup lookup, List<Visual> visuals, List<SkillParticleEmitter> emitters,
                 int slot, long spawn, long landAt, double x, double depth, SkillMotion fall) throws IOException {
            String label = "空投支援 · " + variant.label + " " + (slot + 1);
            double y = depth * .29;
            visuals.add(body(drop, label + " · 下落", 3500, spawn, landAt, x, y, fall, VisualStyle.DEFAULT));
            visuals.add(body(trail, label + " · 下落尾焰", 3501, spawn, landAt, x, y, fall, VisualStyle.DEFAULT));
            x += fall.offsetAt(landAt);
            long ready = landAt + land.duration;
            visuals.add(body(land, label + " · 落地", 3500, landAt, ready, x, y, null, VisualStyle.DEFAULT));
            visuals.add(new Visual(EARTHQUAKE, label + " · 落地震波", 3499, landAt, landAt + quake, x, y).withoutAttack());
            long end = Math.max(ready, landAt + quake);

            // 0x66F7B0 increments the heading counter at every state-8 entry, then 0x670020 checks it on exit.
            boolean healing = variant.heals();
            boolean showGoblin = slot == 0 && !healing;
            long targetStart = landAt;
            double targetX = x + 40;
            double velocity = variant == Variant.HEADING ? 400 : healing ? -225 : 225;
            VisualStyle facing = healing ? FACE_ALLY : VisualStyle.DEFAULT;
            for (int i = 0; i < attackCount; i++) {
                long attackAt = ready + attackDelay;
                visuals.add(new Visual(stay.path, label + " · 待机", 3500, ready, attackAt,
                        x, y, null, 1, true, facing).asAlliedAttack());
                var lunge = new SkillMotion(attackAt, lungeDuration, velocity, 0);
                ready = attackAt + attack.duration;
                if (variant == Variant.HEADING) {
                    visuals.add(new Visual(attack.path, label + " · 冲撞 " + (i + 1) + " 判定",
                            3500, attackAt, ready, x, y, lunge, 1, true).asAlliedAttack());
                } else {
                    visuals.add(body(attack, label + (healing ? " · 接近友方" : " · 自爆预备"),
                            3500, attackAt, ready, x, y, lunge, facing));
                }
                if (variant == Variant.FIRE) {
                    // Only non-heading/non-healing robots attach etc motion 0 in state 8.
                    visuals.add(body(windup, label + " · 自爆预备光效", 3501, attackAt,
                            Math.min(ready, attackAt + windup.duration), x, y, lunge, facing));
                }
                double advance = lunge.offsetAt(ready);
                if (showGoblin && variant == Variant.HEADING) {
                    long hit = attackAt + firstHitDelay;
                    long pushTime = Math.min(400, ready - hit);
                    // Sample a ground knockback that keeps the same target in range for the next charge.
                    var push = new SkillMotion(hit, pushTime, advance * 1000 / pushTime, 0);
                    Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", label + " · 哥布林",
                            targetStart, hit, targetX, y, null);
                    Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", label + " · 哥布林冲撞受击",
                            hit, hit + pushTime, targetX, y, push);
                    targetX += advance;
                    targetStart = hit + pushTime;
                }
                x += advance;
            }

            if (!healing) {
                // 0x66F4C0 uses X+38 and Z+20 when the robot is in attack state 8.
                double blastX = x + 38;
                if (showGoblin) {
                    if (variant == Variant.FIRE) targetX = blastX;
                    Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", label + " · 哥布林",
                            targetStart, ready, targetX, y, null);
                    Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", label + " · 哥布林爆炸受击",
                            ready, ready + blast.duration(), targetX, y, null);
                }
                return Math.max(end, blast.add(visuals, emitters, label, ready, blastX, depth, 0, 20, blastScale));
            }

            // State 18 starts a 500ms recovery appendage on the allied character, then removes the robot.
            // 0x528A70 draws HP art at 60% of character height. 0x529B50 uses the MP art at the character origin.
            String effect = label + (variant == Variant.HP ? " · HP 恢复" : " · MP 恢复");
            long particleAt = ready + recoveryParticleDelay;
            long robotEnd = ready + healBody.duration;
            long effectEnd = ready + healTarget.duration;
            visuals.add(body(healBody, effect + " · 机身", 3500, ready, robotEnd, x, y, null, facing));
            double height = variant == Variant.HP ? (int) (Act1BuffCast.stockHeight("gunner") * .6) : 0;
            visuals.add(body(healTarget, effect + " · 友方特效", 3501, ready, effectEnd, 0, -height, null, VisualStyle.DEFAULT));
            emitters.add(new SkillParticleEmitter(RECOVERY_PARTICLE, effect + " · 友方粒子",
                    particleAt, 1, 0, 0, 0, 60, Double.NaN, 1, true));
            return Math.max(end, Math.max(Math.max(robotEnd, effectEnd), particleAt + 1 + recoveryParticleTail));
        }
    }

    static Scenario load(GameScript.Lookup lookup, List<GameScript.Field> skill, int level, Variant variant,
                         Act1RobotAttack.Blast ordinaryBlast, double ordinaryScale) throws IOException {
        String mob = ROBOT + variant.monster + ".mob";
        if (!mob.equals("monster/" + GameScript.catalog(lookup.read("monster/monster.lst")).get(variant.monsterId))) return null;
        var fields = GameScript.fields(lookup.read(mob));
        var drop = motion(lookup, mob, fields, variant, "etc motion", 1, "drop");
        var trail = motion(lookup, mob, fields, variant, "etc motion", 2, "dropeffect");
        var land = motion(lookup, mob, fields, variant, "etc motion", 3, "land");
        var stay = motion(lookup, mob, fields, variant, "waiting motion", 0, "stay");
        var attack = motion(lookup, mob, fields, variant, "attack motion", 0, "boom");
        long quake = Act1RobotAttack.animationDuration(EARTHQUAKE, lookup);
        int delay = GameScript.integer(GameScript.value(fields, "attack delay"), 0);
        if (drop == null || trail == null || land == null || stay == null || attack == null || quake < 1
                || delay < 1 || delay > 10000) return null;

        // An owned idle robot should not appear as an enemy in the hitbox overlay.
        if (new AnimationTimeline(ResourceScript.animation(lookup.read(stay.path))).hasAttackBoxes()) return null;
        var attackFrames = ResourceScript.animation(lookup.read(attack.path)).frames();
        long lunge = Act1SkillSequence.duration(attackFrames, 3, null);
        if (attackFrames.size() < 4 || lunge < 1 || lunge >= attack.duration) return null;
        long firstHit = 0;
        for (var frame : attackFrames) {
            if (!frame.combat().attack().isEmpty()) break;
            firstHit += frame.delay();
        }
        Motion windup = null, healBody = null, healTarget = null;
        long particleDelay = 0, particleTail = 0;
        int attackCount = 1, recoveryAmount = 0;
        double scale = ordinaryScale;
        Act1RobotAttack.Blast blast = ordinaryBlast;
        switch (variant) {
            case FIRE -> {
                windup = motion(lookup, mob, fields, variant, "etc motion", 0, "boomeffect");
                blast = Act1RobotAttack.blast(lookup, Act1RobotAttack.Robot.MECHADROP_FIRE);
                scale = Act1SkillSequence.levelValue(skill, level, 8, 0, 1, 1000) / 100.0;
                if (windup == null || blast == null || scale <= 0) return null;
            }
            case HEADING -> {
                attackCount = Act1SkillSequence.staticValue(skill, 5, 0);
                if (attackCount < 1 || attackCount > 8 || firstHit >= attack.duration
                        || !attackInfo(lookup, mob, fields, "heading", true)) return null;
            }
            case HP, MP -> {
                if (!attackInfo(lookup, mob, fields, "heal", false)) return null;
                healBody = motion(lookup, mob, fields, variant, "etc motion", 4, "heal");
                String target = RECOVERY_ANIMATION + (variant == Variant.HP ? "heal.ani" : "mpup.ani");
                String targetText = lookup.read(target);
                String particleText = lookup.read(RECOVERY_PARTICLE);
                recoveryAmount = Act1SkillSequence.levelValue(skill, level, variant.recoveryColumn, 0, 1, 100000);
                if (healBody == null || targetText == null || particleText == null || recoveryAmount < 1) return null;
                var art = ResourceScript.animation(targetText);
                long duration = new AnimationTimeline(art).duration();
                if (art.frames().size() < 5 || duration < 1 || duration > 3000) return null;
                healTarget = new Motion(target, duration);
                particleDelay = Act1SkillSequence.duration(art.frames(), 4, null);
                particleTail = SkillParticleEmitter.tail(ParticleScript.parse(particleText));
            }
        }
        return new Scenario(variant, drop, trail, land, stay, attack, windup, healBody, healTarget,
                particleDelay, particleTail, quake, delay, lunge, firstHit, attackCount, recoveryAmount, scale, blast);
    }

    private static boolean attackInfo(GameScript.Lookup lookup, String mob, List<GameScript.Field> fields,
                                      String stem, boolean enemy) throws IOException {
        String path = GameScript.resolve(mob, GameScript.value(fields, "attack info"), lookup);
        if (!(ROBOT + "attackinfo/" + stem + ".atk").equals(path)) return false;
        var attack = GameScript.fields(lookup.read(path));
        return GameScript.integer(GameScript.value(attack, "attack enemy"), -1) == (enemy ? 1 : 0)
                && (enemy || GameScript.integer(GameScript.value(attack, "attack friend"), -1) == 1);
    }

    private static Motion motion(GameScript.Lookup lookup, String mob, List<GameScript.Field> fields,
                                 Variant variant, String tag, int index, String art) throws IOException {
        String path = Act1GunnerMechanicalSupplement.motion(lookup, mob, fields, tag, index,
                variant.art + "_ani/" + art + ".ani");
        long duration = Act1RobotAttack.animationDuration(path, lookup);
        return duration > 0 && duration <= 5000 ? new Motion(path, duration) : null;
    }

    private static Visual body(Motion motion, String label, int layer, long start, long end,
                               double x, double y, SkillMotion movement, VisualStyle style) {
        return new Visual(motion.path, label, layer, start, end, x, y, movement, 1, true, style).withoutAttack();
    }
}
