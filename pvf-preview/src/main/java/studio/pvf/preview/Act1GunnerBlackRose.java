package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** State 10, manager 22229 and four Black Rose soldiers in ACT1. */
final class Act1GunnerBlackRose {
    static final String PATH = "skill/gunner/blackrose.skl";
    static final String FOCUSED = "blackrose:focused";
    private static final String CHR = "character/gunner/gunner.chr";
    private static final String CAST = "character/gunner/effect/animation/blackrose/";
    private static final String SQUAD_EFFECTS = "passiveobject/character/gunner/animation/blackroseeffectconcentric";
    private static final String HIT = "character/gunner/effect/animation/hiteffect/updefault.ani";
    private static final double DEPTH = .29;
    private static final double TARGET_X = 210;

    private record Soldier(int id, String object, String name, int x, int y, int muzzleX, int muzzleZ) {}
    private record Focus(long command, long release, long recovery, long expires) {}

    // Manager table 0x9F4D9C and muzzle coordinates 0xB13860 / 0xB13850.
    private static final List<Soldier> SQUAD = List.of(
            new Soldier(22230, "blackroseshotgun", "散弹枪", 80, 0, 43, 63),
            new Soldier(22231, "blackroserifle1", "步枪一", 0, -40, 43, 63),
            new Soldier(22232, "blackroserifle2", "步枪二", 0, 40, 43, 63),
            new Soldier(22233, "blackrosegrenade", "榴弹枪", -80, 0, 47, 68));

    private Act1GunnerBlackRose() {}

    static Act1SkillSequence find(String text, GameScript.Lookup lookup, Map<String, String> actions, int level,
                                  boolean focused) throws IOException {
        var fields = GameScript.fields(text);
        int cast = Act1SkillSequence.staticValue(fields, 0, 0);
        int recovery = Act1SkillSequence.staticValue(fields, 1, 0);
        int lifetime = Act1SkillSequence.levelValue(fields, level, 0, 0, 1, 50000);
        String prepare = "throw motion 2-1", throwRose = "throw motion 2-2", wait = "waiting motion";
        if (cast < 1 || recovery < 1 || cast + (long) recovery > 10000 || lifetime < 1
                || !actions.keySet().containsAll(List.of(prepare, throwRose, wait))) return null;
        if (!"passiveobject/character/gunner/blackrosemanager.obj".equals(GameEffectRecipes.passiveObject(lookup, 22229))) return null;

        var constants = GameScript.values(GameScript.fields(lookup.read("data/skill/gunner.sd")), "int");
        int delay = setting(constants, "br appear delay"), term = setting(constants, "br appear term");
        int appearTime = setting(constants, "br appear time"), disappearTime = setting(constants, "br disappear time");
        if (delay < 0 || term < 0 || appearTime <= delay + term * 3L || disappearTime < 1) return null;
        long attackStart = cast + appearTime;
        var focus = focused ? focus(fields, lookup, actions, Math.max(cast + recovery, attackStart)) : null;
        if (focused && (focus == null || focus.release() >= cast + (long) lifetime)) return null;
        long expires = focus == null ? cast + (long) lifetime : focus.expires();
        long end = expires + disappearTime;
        if (attackStart >= expires || end > 60000) return null;

        var b = new Clips(lookup);
        for (int i = 1; i <= 2; i++) {
            b.clip(CAST + "cast" + i + ".ani", "黑玫瑰 · 施放准备", 3500 + i, 0, cast, 0, 0, null);
            b.clip(CAST + "blast" + i + ".ani", "黑玫瑰 · 发出指令", 3500 + i, cast, cast + recovery, 0, 0, null);
            b.clip(CAST + "roseeffect" + i + ".ani", "黑玫瑰 · 号令花光", 3500 + i, cast, end, 32, -104, null);
            var trajectory = new SkillMotion(cast + 20, 200, 500, 0, -150, 0, 150);
            b.clip(CAST + "rose" + i + ".ani", "黑玫瑰 · 飞出的玫瑰", 3500 + i, cast, cast + 470, 79, 0, trajectory);
        }
        if (focus != null) {
            var frames = Act1SkillSequence.frames(CHR, actions.get("etc motion:42"), lookup);
            long toss = focus.command() + Act1SkillSequence.duration(frames, 1, null);
            long travel = Math.max(1, (focus.release() - toss) * 6 / 10);
            var trajectory = new SkillMotion(toss + 64, travel, 131000.0 / travel, 0, 49000.0 / travel, 0, 67);
            for (int i = 1; i <= 2; i++) {
                b.clip(CAST + "rose" + i + ".ani", "黑玫瑰 · 集中火力号令", 3500 + i, toss, focus.release(), 29, 0, trajectory);
            }
        }

        long normalEnd = focus == null ? expires : focus.release();
        for (int i = 0; i < SQUAD.size(); i++) {
            Soldier soldier = SQUAD.get(i);
            String object = GameEffectRecipes.passiveObject(lookup, soldier.id());
            if (!(("passiveobject/character/gunner/" + soldier.object() + ".obj").equals(object))) return null;
            var data = GameScript.fields(lookup.read(object));
            var motions = GameScript.values(data, "etc motion");
            var attachments = GameScript.values(data, "string data");
            if (motions.size() < 9 || attachments.size() < 5) return null;
            long appear = cast + delay + term * (long) i;
            long active = Math.min(expires, appear + b.duration(b.resolve(object, motions.get(0))));
            int layer = soldier.y() < 0 ? 3480 : 3520 + i;
            double y = soldier.y() * DEPTH;
            String prefix = "黑玫瑰 · " + soldier.name();
            b.sustain(b.resolve(object, motions.get(0)), prefix + "入场", layer, appear, Math.min(active, attackStart), soldier.x(), y);
            b.sustain(b.resolve(object, motions.get(1)), prefix + "待命", layer, active, attackStart, soldier.x(), y);
            for (int j = 5; j <= 6; j++) {
                b.clip(b.resolve(object, motions.get(j)), prefix + "入场光效", layer + j, appear, expires, soldier.x(), y, null);
            }

            String attack = b.resolve(object, motions.get(2));
            long period = b.duration(attack);
            var attackFrames = b.frames(attack);
            if (attackFrames.size() < 2 || period <= 0) return null;
            long shotOffset = Act1SkillSequence.duration(attackFrames, 1, null);
            int shots = Act1SkillSequence.staticValue(fields, 2 + i * 3, 0);
            if (shots < 1 || shots > 10) return null;
            String particle = b.resolve(object, attachments.get(4));
            long firedUntil = attackStart;
            for (int shot = 0; shot < shots && firedUntil < normalEnd; shot++) {
                long cycle = attackStart + shot * period;
                firedUntil = Math.min(normalEnd, cycle + period);
                b.clip(attack, prefix + "射击", layer, cycle, firedUntil, soldier.x(), y, null);
                long fire = cycle + shotOffset;
                if (fire >= firedUntil) break;
                int muzzlePair = (i == 1 || i == 2) && shot % 2 != 0 ? 2 : 0;
                int muzzleEnd = i == 0 ? 2 : i == 3 ? 3 : muzzlePair + 2;
                for (int j = muzzlePair; j < muzzleEnd; j++) {
                    b.clip(b.resolve(object, attachments.get(j)), prefix + "枪口", layer + 3 + j, fire, normalEnd, soldier.x() + 1, y - 1, null);
                }
                b.shot(particle, prefix, fire, end, soldier);
            }
            b.sustain(b.resolve(object, motions.get(1)), prefix + "驻留", layer, firedUntil, normalEnd, soldier.x(), y);
            if (focus != null) {
                int rate = Act1SkillSequence.staticValue(fields, 4 + i * 3, 0);
                if (rate < 1 || rate > 300) return null;
                long interval = Math.max(1, Math.round(period * 100.0 / rate));
                long muzzleAt = focus.release() + Math.round(shotOffset * 100.0 / rate);
                b.repeat(attack, prefix + "集中射击", layer, focus.release(), expires, interval, soldier.x(), y, true);
                for (int j = 0; j < (i == 3 ? 3 : 2); j++) {
                    b.repeat(b.resolve(object, attachments.get(j)), prefix + "集中射击枪口", layer + 3 + j,
                            muzzleAt, expires, interval, soldier.x() + 1, y - 1, false);
                }
                for (int j = 1; j <= 4; j++) {
                    b.repeat(SQUAD_EFFECTS + j + ".ani", prefix + "集中火力光效", layer + 8 + j,
                            muzzleAt, expires, interval, soldier.x() + 1, y - 1, false);
                }
                for (long fire = muzzleAt; fire < expires; fire += interval) {
                    b.shot(particle, prefix, fire, end, soldier);
                }
            }
            b.clip(b.resolve(object, motions.get(4)), prefix + "退场", layer, expires, end, soldier.x(), y, null);
            for (int j = 7; j <= 8; j++) {
                b.clip(b.resolve(object, motions.get(j)), prefix + "退场光效", layer + j, expires, end, soldier.x(), y, null);
            }
        }
        if (!b.complete || b.hits.isEmpty() || b.emitters.size() > 48) return null;
        b.targets(end);
        var poses = new ArrayList<>(List.of(new Pose(prepare, 0, cast, null, true, null),
                new Pose(throwRose, cast, cast + recovery, null, true, null)));
        if (focus == null) poses.add(new Pose(wait, cast + recovery, end));
        else {
            if (cast + recovery < focus.command()) poses.add(new Pose(wait, cast + recovery, focus.command()));
            poses.add(new Pose("etc motion:42", focus.command(), focus.recovery()));
            if (focus.recovery() < end) poses.add(new Pose(wait, focus.recovery(), end));
        }
        String note = focused
                ? "演示召唤后再次施放。枪手开火后进入连续射击，结束时全队退场，哥布林在步枪二的射击线上"
                : "按 ACT1 的四人站位演示入场、各武器首轮齐射、步枪命中和退场。哥布林位置固定，驻留期间的寻路与重复交火未模拟";
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(b.visuals), List.copyOf(b.emitters), note);
    }

    /** 0x600690 schedules the state-31 command for body frame 8; 0x5A1700 starts the timed firing mode. */
    private static Focus focus(List<GameScript.Field> fields, GameScript.Lookup lookup,
                               Map<String, String> actions, long command) throws IOException {
        int time = Act1SkillSequence.staticValue(fields, 16, 0);
        var frames = Act1SkillSequence.frames(CHR, actions.get("etc motion:42"), lookup);
        if (frames.size() <= 8 || time < 1 || time > 5000) return null;
        long release = command + Act1SkillSequence.duration(frames, 8, null);
        long recovery = command + Act1SkillSequence.duration(frames, frames.size(), null);
        return new Focus(command, release, recovery, release + time);
    }

    private static int setting(List<String> constants, String key) {
        int at = constants.indexOf(key);
        return at < 0 || at + 1 >= constants.size() ? -1 : GameScript.integer(constants.get(at + 1), -1);
    }

    private static final class Clips {
        final GameScript.Lookup lookup;
        final List<Visual> visuals = new ArrayList<>();
        final List<SkillParticleEmitter> emitters = new ArrayList<>();
        final List<Long> hits = new ArrayList<>();
        boolean complete = true;

        Clips(GameScript.Lookup lookup) { this.lookup = lookup; }

        String resolve(String owner, String relative) throws IOException {
            String result = GameScript.resolve(owner, relative, lookup);
            if (result == null) complete = false;
            return result;
        }

        List<ResourceScript.Frame> frames(String path) throws IOException {
            return path == null ? List.of() : ResourceScript.animation(lookup.read(path)).frames();
        }

        long duration(String path) throws IOException {
            return Act1SkillSequence.duration(frames(path), Integer.MAX_VALUE, null);
        }

        void clip(String path, String label, int layer, long start, long limit, double x, double y,
                  SkillMotion motion) throws IOException {
            long length = duration(path);
            if (length <= 0) complete = false;
            long end = Math.min(limit, start + length);
            if (end > start) visuals.add(new Visual(path, label, layer, start, end, x, y, motion, 1, true));
        }

        void sustain(String path, String label, int layer, long start, long end, double x, double y) {
            if (path == null || end <= start) return;
            var held = new VisualStyle(null, end - start, List.of());
            visuals.add(new Visual(path, label, layer, start, end, x, y, null, 1, true, held));
        }

        void repeat(String path, String label, int layer, long start, long end, long period, double x, double y,
                    boolean fitToPeriod) throws IOException {
            var source = frames(path);
            if (source.isEmpty()) { complete = false; return; }
            if (end <= start) return;
            var playback = new ArrayList<>(source);
            long delay = Act1SkillSequence.duration(source, source.size(), null);
            if (!fitToPeriod && delay < period) playback.add(ResourceScript.Frame.still("", 0).withDelay((int) (period - delay)));
            var animation = new ResourceScript.Animation(false, List.copyOf(playback), List.of());
            var style = new VisualStyle(null, fitToPeriod ? period : 0, List.of(), Map.of(), false, period, animation);
            visuals.add(new Visual(path, label, layer, start, end, x, y, null, 1, true, style));
        }

        void shot(String particle, String label, long fire, long end, Soldier soldier) throws IOException {
            if (soldier.id() == 22233) grenade(particle, label, fire, soldier);
            else projectile(particle, label, fire, soldier);
            if (soldier.id() != 22232) return;
            // The horizontal rifle object has a 33px box and the Goblin's facing-relative box reaches 22px forward.
            long hit = fire + (long) Math.ceil(1000 * (TARGET_X - 22 - 33 - soldier.muzzleX()) / 800.0);
            if (hit < end) {
                hits.add(hit);
                clip(HIT, label + " · 哥布林受击", 3610, hit, end, TARGET_X - 20, soldier.y() * DEPTH - 55, null);
            }
        }

        void projectile(String particle, String label, long fire, Soldier soldier) {
            if (particle == null) return;
            emitters.add(new SkillParticleEmitter(particle, label + "弹道", fire, 1, 0,
                    soldier.x() + soldier.muzzleX(), soldier.y() * DEPTH, soldier.muzzleZ(), Double.NaN, 1));
        }

        void grenade(String particle, String label, long fire, Soldier soldier) throws IOException {
            if (particle == null) return;
            var p = ParticleScript.parse(lookup.read(particle));
            String object = GameEffectRecipes.passiveObject(lookup, 22236);
            if (object == null || p.axes().isEmpty()) { complete = false; return; }
            var fields = GameScript.fields(lookup.read(object));
            var impacts = GameScript.values(fields, "etc motion");
            if (impacts.size() != 2 || p.gravity() <= 0) { complete = false; return; }
            double speed = p.axes().get(0).start();
            double radians = Math.toRadians(p.angles().y());
            double vx = speed * Math.cos(radians), vz = speed * Math.sin(radians);
            long flight = (long) Math.ceil(1000 * (vz + Math.sqrt(vz * vz + 2 * p.gravity() * soldier.muzzleZ())) / p.gravity());
            var motion = new SkillMotion(fire, flight, vx, 0, vz, -p.gravity(), soldier.muzzleZ());
            double x = soldier.x() + soldier.muzzleX(), y = soldier.y() * DEPTH;
            long hit = fire + flight;
            String body = resolve(object, GameScript.value(fields, "basic motion"));
            if (body != null) visuals.add(new Visual(body, label + "弹道", 3560, fire, hit, x, y, motion, 1, true));
            for (String impact : impacts) {
                clip(resolve(object, impact), label + "榴弹爆炸", 3560, hit, Long.MAX_VALUE, x + motion.offsetAt(hit), y, null);
            }
        }

        void targets(long end) throws IOException {
            long available = 0;
            for (int i = 0; i < hits.size(); i++) {
                long hit = hits.get(i);
                if (available < hit) target("stay", available, hit);
                long next = i + 1 < hits.size() ? hits.get(i + 1) : end;
                available = Math.min(next, hit + 250);
                target("damage1", hit, available);
            }
            if (available < end) target("stay", available, end);
        }

        void target(String animation, long start, long end) throws IOException {
            String path = Act1SwordAsuraSupplement.GOBLIN + animation + ".ani";
            if (duration(path) <= 0) { complete = false; return; }
            var facing = new VisualStyle(null, 0, List.of(), Map.of(), true);
            visuals.add(new Visual(path, "黑玫瑰 · 哥布林", 3550, start, end, TARGET_X, 40 * DEPTH, null, 1, true, facing));
        }
    }
}
