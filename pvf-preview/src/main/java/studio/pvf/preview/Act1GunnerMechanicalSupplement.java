package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1RobotAttack.Robot;
import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;

import static studio.pvf.preview.Act1RobotAttack.ROBOT;

/** Native Gunner state 25 (0x600690 / 0x5F9CB0) and the summoned robots' own state clocks. */
final class Act1GunnerMechanicalSupplement {
    private static final String CHARACTER = "character/gunner/gunner.chr";
    private static final String SET = "etc motion:32";
    private static final String WAIT = "waiting motion";
    private static final String RX_EXPIRY = "robot-rx78:expiry";
    private static final double SPAWN_X = 30;

    private Act1GunnerMechanicalSupplement() {}

    static List<GameEffectRecipes.Effect> choices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1 || !path.equals("skill/gunner/robotrx78.skl") || !Act1SkillSequence.matches(path, "gunner", 27, lookup))
            return List.of();
        return List.of(new GameEffectRecipes.Effect(RX_EXPIRY, "RX-78 · 无目标自爆", "", path,
                "在原地等待存活时间耗尽，展示没有近身目标时的爆炸位置"));
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup, Map<String, String> actions,
                                  int level, String choice) throws IOException {
        int id = switch (path) {
            case "skill/gunner/robotrx78.skl" -> 27;
            case "skill/gunner/robotez8.skl" -> 28;
            case "skill/gunner/robotexs.skl" -> 29;
            case "skill/gunner/robottempester.skl" -> 63;
            default -> 0;
        };
        boolean expiry = id == 27 && choice.equals(RX_EXPIRY);
        if (id == 0 || !choice.isEmpty() && !expiry || !Act1SkillSequence.matches(path, "gunner", id, lookup)) return null;
        var fields = GameScript.fields(text);
        String type = GameScript.flag(GameScript.value(fields, "type"));
        if (!type.isEmpty() && !type.equals("active")) return null;
        String set = actions.get(SET);
        if (set == null || !GameScript.normalize(set).endsWith("/setrobot.ani") || !actions.containsKey(WAIT)
                || Act1SkillSequence.frames(CHARACTER, set, lookup).isEmpty()) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 0);
        if (cast <= 0 || cast > 10000) return null;
        return switch (id) {
            case 27 -> rx78(fields, lookup, level, cast, expiry);
            case 28 -> ez8(fields, lookup, level, cast, 0, "Ez-8 自爆者", false);
            case 29 -> exs(fields, lookup, level, cast);
            case 63 -> tempester(fields, lookup, level, cast);
            default -> throw new AssertionError(id);
        };
    }

    private static Act1SkillSequence rx78(List<GameScript.Field> fields, GameScript.Lookup lookup, int level,
                                         long cast, boolean expiry) throws IOException {
        String mob = ROBOT + "rx78.mob";
        var monster = monsterFields(lookup, mob, 60002);
        if (monster.isEmpty()) return null;
        String idle = motion(lookup, mob, monster, WAIT, 0, "rx78_ani/stay.ani");
        var blast = Act1RobotAttack.blast(lookup, Robot.RX78);
        if (idle == null || blast == null) return null;
        int lifetime = Act1SkillSequence.levelValue(fields, level, 2, 0, 1, 45000);
        if (lifetime < 1) return null;
        String label = "RX-78 追击者";
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        long detonate = cast + lifetime;
        double x = SPAWN_X;
        double lift = 30;
        long windup = cast + GameScript.integer(GameScript.value(monster, "attack delay"), 0);
        String attack = motion(lookup, mob, monster, "attack motion", 0, "rx78_ani/boom.ani");
        long duration = Act1RobotAttack.animationDuration(attack, lookup);
        // 0x670020 waits for the state-8 ANI to end, then 0x66F4C0 offsets this branch by X+38/Z+20.
        if (!expiry && windup + duration < detonate) {
            if (duration < 1 || windup <= cast) return null;
            String effect = motion(lookup, mob, monster, "etc motion", 0, "rx78_ani/boomeffect.ani");
            var frames = ResourceScript.animation(lookup.read(attack)).frames();
            if (effect == null || frames.size() < 4) return null;
            // 0x66F7B0 uses X velocity 225 until 0x670020 stops the movement at frame 3.
            long stop = Act1SkillSequence.duration(frames, 3, null);
            var lunge = new SkillMotion(windup, stop, 225, 0);
            body(visuals, idle, label + " · 机身", 1, cast, windup, SPAWN_X, 0);
            detonate = windup + duration;
            visuals.add(new Visual(attack, label + " · 自爆预备", 1, windup, detonate, SPAWN_X, 0, lunge, 1, true).withoutAttack());
            visuals.add(new Visual(effect, label + " · 自爆预备光效", 1001, windup, detonate, SPAWN_X, 0, lunge, 1, true).withoutAttack());
            x += Math.floor(lunge.offsetAt(detonate)) + 38;
            lift = 20;
        } else {
            body(visuals, idle, label + " · 机身", 1, cast, detonate, SPAWN_X, 0);
        }
        long end = blast.add(visuals, emitters, label, detonate, x, 0, 0, lift, 1);
        if (lift == 20) {
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", label + " · 近身演示目标", 0, detonate, 100, null);
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", label + " · 爆炸受击", detonate, end, 100, null);
        }
        return sequence(cast, end, visuals, emitters, expiry
                ? "RX-78 在无目标场景按当前等级存活时间自爆，展示原版爆炸判定和碎片。"
                : "RX-78 出场后演示近身目标触发的自爆动作及向前偏移的爆炸判定。触发时间按原始攻击间隔取样，未模拟寻路。");
    }

    static Act1SkillSequence ez8(List<GameScript.Field> fields, GameScript.Lookup lookup, int level, long cast,
                                 int addedTime, String label, boolean target) throws IOException {
        String mob = ROBOT + "ez8.mob";
        var monster = monsterFields(lookup, mob, 60003);
        if (monster.isEmpty()) return null;
        String idle = motion(lookup, mob, monster, WAIT, 0, "ez8_ani/stay1.ani");
        String quick = motion(lookup, mob, monster, "etc motion", 0, "ez8_ani/stay2.ani");
        String arm = motion(lookup, mob, monster, "etc motion", 1, "ez8_ani/boom.ani");
        var blast = Act1RobotAttack.blast(lookup, Robot.EZ8);
        if (idle == null || quick == null || arm == null || blast == null) return null;
        int timer = ez8Timer(fields, level, addedTime);
        double scale = ez8Scale(fields, level);
        if (timer <= 500 || scale <= 0 || cast <= 0 || cast > 10000) return null;
        long detonate = cast + timer;
        long accelerate = Math.max(cast, detonate - 5500);
        long arming = Math.max(cast, detonate - 600);
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        // 0x66D690 adds 500ms to the skill timer; 0x66DE50 selects Stay2 and Boom by time remaining.
        if (accelerate > cast) body(visuals, idle, label + " · 机身", 1, cast, accelerate, SPAWN_X, 0);
        if (arming > accelerate) body(visuals, quick, label + " · 加速倒计时", 1, accelerate, arming, SPAWN_X, 0);
        body(visuals, arm, label + " · 引爆预备", 1, arming, detonate, SPAWN_X, 0);
        Act1GunnerUtilityCast.addCountdown(visuals, cast, timer, SPAWN_X, label);
        long end = blast.add(visuals, emitters, label, detonate, SPAWN_X, 0, 0, 30, scale);
        if (target) {
            double targetX = SPAWN_X + 54;
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", label + " · 演示目标", 0, detonate, targetX, null);
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", label + " · 爆炸受击", detonate, end, targetX, null);
        }
        return sequence(cast, end, visuals, emitters, label + "按" + (target ? " Ez-8 Lv.1 与延迟技能的" : "当前等级的")
                + "原版计时播放倒计时、加速、引爆预备和爆炸判定。");
    }

    static int ez8Timer(List<GameScript.Field> fields, int level, int addedTime) {
        int countdown = Act1SkillSequence.levelValue(fields, level, 2, 0, 1, 20000);
        return countdown == 0 ? 0 : countdown + addedTime + 500;
    }

    static double ez8Scale(List<GameScript.Field> fields, int level) {
        return Act1SkillSequence.levelValue(fields, level, 3, 0, 1, 1000) / 100.0;
    }

    private static Act1SkillSequence exs(List<GameScript.Field> fields, GameScript.Lookup lookup, int level, long cast)
            throws IOException {
        String mob = ROBOT + "exs.mob";
        String dir = ROBOT + "exs_ani/";
        var monster = monsterFields(lookup, mob, 60004);
        if (monster.isEmpty()) return null;
        String idle = motion(lookup, mob, monster, WAIT, 0, "exs_ani/body.ani");
        var bullet = projectile(lookup, 22016, "exsviperbullet", "exs_bullet");
        var blast = Act1RobotAttack.blast(lookup, Robot.EXS);
        int count = Act1SkillSequence.staticValue(fields, 1, 0);
        int interval = Act1SkillSequence.staticValue(fields, 0, 0);
        int recovery = Act1SkillSequence.staticValue(fields, 2, 0);
        int lifetime = Act1SkillSequence.levelValue(fields, level, 3, 0, 1, 45000);
        if (idle == null || bullet == null || blast == null || count < 1 || count > 20
                || interval < 1 || recovery < 1 || lifetime < 1) return null;
        long detonate = cast + lifetime;
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        // The stock Attack.ani uses the same still body as Body.ani; the attached cannon changes state.
        body(visuals, idle, "Ex-S 毒蛇炮 · 机身", 1, cast, detonate, SPAWN_X, 0);
        long shot = cast;
        // 0x66D010 / 0x66CCF0 restart each configured volley after the recovery and emit 22016.
        while (shot < detonate && visuals.size() + 3 + 2 * count <= 230) {
            long last = Math.min(detonate, shot + (count - 1L) * interval + 1);
            body(visuals, dir + "cannonattack.ani", "Ex-S 毒蛇炮 · 炮管开火", 0, shot, last, SPAWN_X, -60);
            for (int i = 0; i < count && shot + i * (long)interval < detonate; i++) {
                long fire = shot + i * (long)interval;
                body(visuals, dir + "shoot" + (i % 2 + 1) + ".ani", "Ex-S 毒蛇炮 · 枪口火光", 3501,
                        fire, fire + 140, SPAWN_X + 75, -60);
                flying(visuals, bullet, "Ex-S 毒蛇炮 · 子弹", fire, SPAWN_X + 75, -.29, 60, 0, 0);
            }
            long ready = Math.min(detonate, last - 1 + recovery);
            body(visuals, dir + "cannonstand.ani", "Ex-S 毒蛇炮 · 待机炮管", 0, last, ready, SPAWN_X, -60);
            body(visuals, dir + "smoke.ani", "Ex-S 毒蛇炮 · 炮管烟雾", 2, last, Math.min(ready, last + 540), SPAWN_X, -60);
            shot = ready;
        }
        if (shot < detonate) {
            body(visuals, dir + "cannonstand.ani", "Ex-S 毒蛇炮 · 待机炮管", 0, shot, detonate, SPAWN_X, -60);
        }
        String destroyed = dir + "cannondie.ani";
        body(visuals, destroyed, "Ex-S 毒蛇炮 · 炮管毁坏", 0, detonate,
                detonate + Act1RobotAttack.animationDuration(destroyed, lookup), SPAWN_X, -60);
        long end = blast.add(visuals, emitters, "Ex-S 毒蛇炮", detonate, SPAWN_X, 0, 0, 30, 1);
        if (!allArt(visuals, lookup)) return null;
        return sequence(cast, end, visuals, emitters, "Ex-S 按原版连射间隔、休息时间和存活时长展示平射与自爆。"
                + "枪口火光交替取样，弹道固定朝前。" + (shot < detonate ? "连续开火达到轨道上限，保留最后的自爆阶段。" : ""));
    }

    private static Act1SkillSequence tempester(List<GameScript.Field> fields, GameScript.Lookup lookup, int level, long cast)
            throws IOException {
        String mob = ROBOT + "tempester.mob";
        String dir = ROBOT + "tempesterani/";
        var monster = monsterFields(lookup, mob, 60018);
        if (monster.isEmpty()) return null;
        String idle = motion(lookup, mob, monster, WAIT, 0, "tempesterani/bodystand.ani");
        var gun = projectile(lookup, 22224, "tempestergunbullet", "tempestergun");
        var missile = projectile(lookup, 22225, "tempestermissile", "tempestermissile");
        var blast = Act1RobotAttack.blast(lookup, Robot.TEMPESTER);
        String smoke = "passiveobject/character/gunner/particle/tempestermissilesmoke.ptl";
        String smokeText = lookup.read(smoke);
        int height = GameScript.integer(GameScript.value(monster, "floating height"), 0);
        int delay = GameScript.integer(GameScript.value(monster, "attack delay"), 0);
        int gunInterval = Act1SkillSequence.staticValue(fields, 0, 0), gunCount = Act1SkillSequence.staticValue(fields, 1, 0);
        int missileInterval = Act1SkillSequence.staticValue(fields, 2, 0), missileCount = Act1SkillSequence.staticValue(fields, 3, 0);
        int rushSpeed = Act1SkillSequence.staticValue(fields, 4, 0);
        int lifetime = Act1SkillSequence.levelValue(fields, level, 5, 0, 1, 45000);
        if (idle == null || gun == null || missile == null || blast == null || smokeText == null || height < 1 || height > 1000
                || delay < 1 || gunInterval < 1 || gunCount < 1 || gunCount > 20 || missileInterval < 1 || missileCount < 1
                || missileCount > 12 || rushSpeed < 1 || lifetime < 1) return null;
        String label = "空战机械：风暴";
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        long expires = cast + lifetime;
        long gunStart = Math.min(expires, cast + delay);
        long gunFire = gunStart + 200 + gunInterval;
        long gunEnd = gunFire + (gunCount - 1L) * gunInterval + 200;
        long missileStart = gunEnd + delay;
        long missileFire = missileStart + 400;
        long missileEnd = missileFire + (missileCount - 1L) * missileInterval + 300;
        hover(visuals, idle, dir, label, cast, gunStart, height);
        if (gunEnd <= expires) {
            // 0x670470 / 0x671AD0: horizontal aim selects BodyGun2 and muzzle table entries X61/Z52.
            body(visuals, dir + "bodygun2.ani", label + " · 机枪", 1, gunStart, gunEnd, SPAWN_X, -height);
            body(visuals, dir + "effectboostattack.ani", label + " · 攻击推进器", 0, gunStart, gunEnd, SPAWN_X, -height);
            for (int i = 0; i < gunCount; i++) {
                long fire = gunFire + i * (long)gunInterval;
                body(visuals, dir + "effectgunshoot.ani", label + " · 机枪火光", 3501, fire, fire + 140,
                        SPAWN_X + 61, -height - 52);
                flying(visuals, gun, label + " · 机枪子弹", fire, SPAWN_X + 61, 0, height + 52, 0, 0);
            }
        } else {
            gunEnd = gunStart;
        }
        if (missileEnd <= expires && gunEnd > gunStart) {
            hover(visuals, idle, dir, label, gunEnd, missileStart, height);
            body(visuals, dir + "bodymissile.ani", label + " · 导弹舱", 1, missileStart, missileEnd, SPAWN_X, -height);
            body(visuals, dir + "effectboostattack.ani", label + " · 攻击推进器", 0, missileStart, missileEnd, SPAWN_X, -height);
            for (int i = 0; i < missileCount; i++) {
                long fire = missileFire + i * (long)missileInterval;
                double x = SPAWN_X + (i % 2 == 0 ? -15 : 24);
                double z = height + (i % 2 == 0 ? 60 : 64);
                double y = i % 2 == 0 ? 0 : -.29;
                double vz = (45 - z) * 1000 / missile.life;
                SkillMotion movement = flying(visuals, missile, label + " · 导弹", fire, x, y, z, -y / .29, vz);
                emitters.add(new SkillParticleEmitter(smoke, label + " · 导弹尾烟", fire, missile.life, 0, x - 24,
                        y, z, Double.NaN, 1, true, 1, false, movement));
            }
        } else {
            missileEnd = gunEnd;
        }
        long rush = expires + 500;
        hover(visuals, idle, dir, label, missileEnd, rush, height);
        // 0x671D60 state 16 arms for 500ms. State 17 targets a fixed sample at X+160/Z=15.
        double targetX = SPAWN_X + 160, targetZ = 15;
        long travel = Math.max((long)Math.ceil(160000.0 / rushSpeed),
                (long)Math.ceil(1000 * Math.abs(targetZ - height) / (rushSpeed * .7)));
        long detonate = rush + travel;
        var move = new SkillMotion(rush, travel, 160000.0 / travel, 0, (targetZ - height) * 1000 / travel, 0, height);
        String dash = dir + "bodydash.ani";
        var dashFrames = ResourceScript.animation(lookup.read(dash)).frames();
        if (dashFrames.isEmpty()) return null;
        visuals.add(new Visual(dash, label + " · 自爆冲刺判定", 1, rush, detonate, SPAWN_X, 0, move, 1, true).asAlliedAttack());
        visuals.add(new Visual(dir + "effectdash.ani", label + " · 冲刺火光", 2, rush, detonate, SPAWN_X, 0, move, 1, true).withoutAttack());
        long boostShift = Math.min(detonate, rush + dashFrames.get(0).delay());
        if (boostShift > rush) visuals.add(new Visual(dir + "effectboostfront1.ani", label + " · 冲刺推进器", 0,
                rush, boostShift, SPAWN_X, 0, move, 1, true).withoutAttack());
        if (boostShift < detonate) visuals.add(new Visual(dir + "effectboostfront2.ani", label + " · 冲刺推进器", 0,
                boostShift, detonate, SPAWN_X, 0, move, 1, true).withoutAttack());
        long end = blast.add(visuals, emitters, label, detonate, targetX, 0, targetZ, 30, 1);
        if (!allArt(visuals, lookup)) return null;
        return sequence(cast, end, visuals, emitters, "风暴演示一轮机枪、一轮导弹及存活到期后的冲刺自爆。"
                + "目标位置与进攻顺序固定取样，弹道使用原始参数。");
    }

    private static void hover(List<Visual> visuals, String idle, String dir, String label, long start, long end, int height) {
        body(visuals, idle, label + " · 机身", 1, start, end, SPAWN_X, -height);
        body(visuals, dir + "effectbooststay.ani", label + " · 悬停推进器", 0, start, end, SPAWN_X, -height);
    }

    private static void body(List<Visual> visuals, String path, String label, int layer, long start, long end, double x, double y) {
        if (end > start) visuals.add(new Visual(path, label, layer, start, end, x, y).withoutAttack());
    }

    private static boolean allArt(List<Visual> visuals, GameScript.Lookup lookup) throws IOException {
        for (String path : visuals.stream().map(Visual::path).distinct().toList()) {
            if (Act1RobotAttack.animationDuration(path, lookup) < 1) return false;
        }
        return true;
    }

    private static List<GameScript.Field> monsterFields(GameScript.Lookup lookup, String path, int id) throws IOException {
        if (!path.equals("monster/" + GameScript.catalog(lookup.read("monster/monster.lst")).get(id))) return List.of();
        return GameScript.fields(lookup.read(path));
    }

    static String motion(GameScript.Lookup lookup, String mob, List<GameScript.Field> fields, String tag, int index,
                         String expected) throws IOException {
        var refs = GameScript.values(fields, tag);
        String path = refs.size() > index ? GameScript.resolve(mob, refs.get(index), lookup) : null;
        return (ROBOT + expected).equals(path) && Act1RobotAttack.animationDuration(path, lookup) > 0 ? path : null;
    }

    private record Projectile(String path, long life, ParticleScript motion) {}

    private static Projectile projectile(GameScript.Lookup lookup, int id, String stem, String particle) throws IOException {
        String path = Act1RobotAttack.passiveMotion(lookup, id, stem);
        String source = lookup.read(ROBOT + "particle/" + particle + ".ptl");
        if (path == null || source == null) return null;
        var spec = ParticleScript.parse(source);
        return spec.life() > 0 && spec.life() <= 5000 && spec.axes().get(0).type().equals("straight line")
                ? new Projectile(path, (long)spec.life(), spec) : null;
    }

    private static SkillMotion flying(List<Visual> visuals, Projectile projectile, String label, long fire,
                                      double x, double y, double z, double depthVelocity, double verticalVelocity) {
        var depth = new ParticleScript.Axis("straight line", depthVelocity, depthVelocity, 0, 0);
        var height = new ParticleScript.Axis("straight line", verticalVelocity, verticalVelocity, 0, 0);
        var movement = SkillMotion.orbit(fire, projectile.life, new SkillMotion.Orbit(projectile.motion.axes().get(0),
                depth, projectile.motion.term(), projectile.motion.changing(), 0, height));
        visuals.add(new Visual(projectile.path, label, 3500, fire, fire + projectile.life, x, y - z, movement, 1, true));
        return movement;
    }

    private static Act1SkillSequence sequence(long cast, long end, List<Visual> visuals, List<SkillParticleEmitter> emitters,
                                             String note) {
        if (end > 60000) return null;
        return new Act1SkillSequence(List.of(new Pose(SET, 0, cast, null, true, null), new Pose(WAIT, cast, end)),
                List.copyOf(visuals), List.copyOf(emitters), note);
    }
}
