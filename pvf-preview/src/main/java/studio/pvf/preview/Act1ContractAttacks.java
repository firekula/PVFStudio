package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1MageSupplement.SummonAttackResult;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** ACT1 contract-summon attack indices, frame cues, and their passive objects. */
final class Act1ContractAttacks {
    private static final String PREFIX = "mage-contract:";
    private static final String ATTACK = "attack motion";
    private static final String ETC = "etc motion";
    private static final String COMMON = "passiveobject/common/";
    private static final String HODOR = "monster/goblin/";
    private static final String LUIS = "monster/soceress/";
    private static final String AUXO = "monster/monsterflower/";
    private static final String UNSUMMON = "monster/common/animation/unsummon.ani";
    private static final int BODY_LAYER = 3550;

    private enum Move {
        HODOR_SPEED(900, ATTACK, 1, "hodor-speed", "赫德尔 · 加速", 6),
        HODOR_STONE(900, ATTACK, 2, "hodor-stone", "赫德尔 · 投石", 7),
        HODOR_TRIPLE(900, ATTACK, 3, "hodor-triple", "赫德尔 · 三连投石", 8),
        HODOR_BOMB(900, ATTACK, 4, "hodor-bomb", "赫德尔 · 爆弹", 9),
        SANDOR_THRUST(901, ATTACK, 1, "sandor-thrust", "桑德尔 · 直刺", 13),
        SANDOR_GUARD(901, ETC, 0, "sandor-guard", "桑德尔 · 防御", -1),
        SANDOR_HEAL(901, ATTACK, 2, "sandor-heal", "桑德尔 · 自我恢复", 14),
        SANDOR_RESIST(901, ATTACK, 3, "sandor-resist", "桑德尔 · 异常抗性", 14),
        SANDOR_TAUNT(901, ATTACK, 4, "sandor-taunt", "桑德尔 · 挑拨光环", 15),
        SANDOR_DEFENSE(901, ATTACK, 5, "sandor-defense", "桑德尔 · 防御光环", 16),
        LUIS_ICE(902, ATTACK, 1, "luis-ice", "路易丝 · 冰冻针刺", 12),
        LUIS_FIRE(902, ATTACK, 2, "luis-fire", "路易丝 · 火球", 13),
        LUIS_INT(902, ATTACK, 3, "luis-int", "路易丝 · 智力光环", 14),
        LUIS_MP(902, ATTACK, 4, "luis-mp", "路易丝 · MP 恢复光环", 15),
        LUIS_METEOR_EXPIRY(902, "waiting motion", 0, "luis-meteor-expiry", "路易丝 · 消失时的陨石", -1),
        LUIS_METEOR_DEATH(902, "down motion", 0, "luis-meteor-death", "路易丝 · 被击倒时的陨石", -1),
        AUXO_ROOT(903, ATTACK, 1, "auxo-root", "袄索 · 藤根", 5),
        AUXO_POISON(903, ATTACK, 2, "auxo-poison", "袄索 · 毒虫与混乱毒气", 6);

        final int monsterId;
        final String tag;
        final int index;
        final String key;
        final String label;
        final int unlockIndex;

        Move(int monsterId, String tag, int index, String key, String label, int unlockIndex) {
            this.monsterId = monsterId;
            this.tag = tag;
            this.index = index;
            this.key = PREFIX + key;
            this.label = label;
            this.unlockIndex = unlockIndex;
        }
    }

    private Act1ContractAttacks() {}

    static List<GameEffectRecipes.Effect> choices(String skill, int monsterId, String monster,
                                                   GameScript.Lookup lookup) throws IOException {
        var fields = GameScript.fields(lookup.read(monster));
        var choices = new ArrayList<GameEffectRecipes.Effect>();
        for (Move move : Move.values()) {
            if (move.monsterId != monsterId) continue;
            if (append(monsterId, monster, move.key, lookup, 0, 0,
                    new ArrayList<>(), new ArrayList<>()) == null) continue;
            int level = value(fields, move.unlockIndex, 1);
            String note = switch (move) {
                case LUIS_METEOR_EXPIRY -> "演示寿命结束时向一个符合条件的目标落下陨石";
                case LUIS_METEOR_DEATH -> "演示首次被击倒时向两个符合条件的目标落下陨石";
                case HODOR_SPEED, SANDOR_RESIST, SANDOR_DEFENSE, LUIS_INT, LUIS_MP ->
                        (level > 1 ? "原版 Lv." + level + " 解锁。" : "") + "演示施放动作，属性变化由战斗系统处理";
                default -> level > 1 ? "演示原版 Lv." + level + " 解锁后的行为" : "演示一次原版召唤兽行为";
            };
            choices.add(new GameEffectRecipes.Effect(move.key, move.label, "", skill, note));
        }
        return List.copyOf(choices);
    }

    static SummonAttackResult append(int monsterId, String monster, String choice, GameScript.Lookup lookup,
                                      long start, double x, List<Visual> visuals, List<SkillParticleEmitter> emitters)
            throws IOException {
        for (Move move : Move.values()) {
            if (monsterId != move.monsterId || !move.key.equals(choice)) continue;
            var scene = new AttackScene(lookup, start, x, move.label);
            var monsterFields = GameScript.fields(lookup.read(monster));
            String body = motion(monster, monsterFields, move.tag, move.index, lookup);
            var animation = readAnimation(body, lookup);
            if (animation.frames().isEmpty()) return null;
            long bodyEnd = start + duration(animation);
            if (move == Move.LUIS_METEOR_DEATH) bodyEnd = frameTime(animation, start, 2) + 500;
            if (move == Move.LUIS_METEOR_EXPIRY) {
                long vanish = scene.once(UNSUMMON, 3600, start, x, 0, false);
                if (vanish == start) return null;
                bodyEnd = vanish;
            }
            SkillMotion movement = null;
            if (move == Move.SANDOR_THRUST) {
                // 0x6A79C0 supplies 1000 and 0x6A5C00 sets acceleration -3000 when frame 1 starts.
                long dash = frameTime(animation, start, 1);
                movement = new SkillMotion(dash, Math.max(0, Math.min(333, bodyEnd - dash)), 1000, -3000);
            }
            if (move == Move.AUXO_POISON) {
                scene.cutoff(body, BODY_LAYER, start, x, 0, 3, Integer.MAX_VALUE);
            } else {
                scene.layer(body, BODY_LAYER, start, bodyEnd, x, 0, movement, false, move == Move.LUIS_METEOR_EXPIRY);
            }
            scene.equipment(monsterId, move.tag, move.index, bodyEnd, movement, move == Move.SANDOR_THRUST);
            boolean complete = switch (move) {
                case HODOR_STONE, HODOR_TRIPLE -> hodorStones(scene, move, animation);
                case HODOR_BOMB -> hodorBomb(scene, monster, monsterFields, animation);
                case SANDOR_THRUST -> sandorThrust(scene, bodyEnd);
                case HODOR_SPEED, SANDOR_GUARD, SANDOR_RESIST, SANDOR_DEFENSE, LUIS_INT, LUIS_MP -> true;
                case SANDOR_HEAL, SANDOR_TAUNT -> sandorSupport(scene, move, monsterFields, animation);
                case LUIS_ICE -> luisIce(scene);
                case LUIS_FIRE -> luisFire(scene, monsterFields, animation);
                case LUIS_METEOR_EXPIRY, LUIS_METEOR_DEATH -> luisMeteor(scene, move);
                case AUXO_ROOT -> auxoRoot(scene, animation);
                case AUXO_POISON -> auxoPoison(scene, monsterFields, animation, bodyEnd);
            };
            if (!complete) return null;
            long end = Math.max(bodyEnd, scene.end);
            visuals.addAll(scene.visuals);
            emitters.addAll(scene.emitters);
            double bodyX = x + (movement == null ? 0 : movement.offsetAt(bodyEnd));
            return new SummonAttackResult(bodyEnd, end, bodyX,
                    move == Move.LUIS_METEOR_EXPIRY || move == Move.LUIS_METEOR_DEATH);
        }
        return null;
    }

    /** 0x6D7A80 queues frames 2/6/10, and 0x6D7ED0 launches object 30372 at (+20,0,60). */
    private static boolean hodorStones(AttackScene scene, Move move, ResourceScript.Animation body) throws IOException {
        String object = GameEffectRecipes.passiveObject(scene.lookup, 30372);
        var stone = Act1SkillSequence.objectMotions(scene.lookup, 30372);
        var flight = scene.projectile(HODOR + "particle/throwstone.ptl");
        if (stone.isEmpty() || flight == null || object == null) return false;
        var fields = GameScript.fields(scene.lookup.read(object));
        String debris = GameScript.resolve(object, GameScript.value(fields, "destroy particle"), scene.lookup);
        double target = scene.x + 220;
        long travel = Math.min(flight.life, Math.round(1000 * (target - scene.x - 20 - 12) / flight.speed));
        for (int frame : move == Move.HODOR_TRIPLE ? new int[]{2, 6, 10} : new int[]{2}) {
            long release = frameTime(body, scene.start, frame);
            long hit = release + travel;
            var motion = new SkillMotion(release, travel, flight.speed, 0, 0, 0, 60);
            scene.layer(stone.get(0), 3600, release, hit, scene.x + 20, 0, motion, true, true);
            scene.particle(debris, hit, 1, target - 12, 0, 60, null);
        }
        scene.target(target, scene.end);
        return true;
    }

    /** 0x6D6A00 calculates the launch speed from target range; 0x5D25A0 switches to BombLand. */
    private static boolean hodorBomb(AttackScene scene, String monster, List<GameScript.Field> fields,
                                     ResourceScript.Animation body) throws IOException {
        var bomb = Act1SkillSequence.objectMotions(scene.lookup, 30370);
        var blast = Act1SkillSequence.objectMotions(scene.lookup, 30371);
        var flight = scene.projectile(HODOR + "particle/aimingbomb.ptl");
        if (bomb.size() < 2 || blast.size() < 4 || flight == null || flight.gravity <= 0 || flight.rise <= 0) return false;
        long release = frameTime(body, scene.start, 2);
        double target = scene.x + 220;
        double height = 60;
        double rise = 400;
        double duration = (rise + Math.sqrt(rise * rise + 2 * flight.gravity * height)) / flight.gravity;
        long travel = Math.round(1000 * duration);
        double speed = Math.min(180, (target - scene.x) / (4 * flight.rise / flight.gravity));
        var arc = new SkillMotion(release, travel, speed, 0, rise, -flight.gravity, height);
        long land = release + travel;
        double landedX = scene.x + 20 + arc.offsetAt(land);
        scene.layer(bomb.get(0), 3600, release, land, scene.x + 20, 0, arc, true, true);
        long explode = scene.once(bomb.get(1), 3600, land, landedX, 0, false);
        scene.once(blast.get(0), 3600, explode, landedX, 0, true);
        for (int i = 1; i < blast.size(); i++) {
            scene.once(blast.get(i), 3599 + i, explode, landedX, 0, false);
        }
        for (String particle : scene.objectParticles(30371)) {
            scene.particle(particle, explode, 1, landedX, 0, 0, null);
        }
        String held = motion(monster, fields, ETC, 0, scene.lookup);
        scene.layer(held, 3700, scene.start, release, scene.x, 0, null, false, false);
        scene.target(landedX, scene.end);
        return true;
    }

    /** 0x6A7690 delegates index 1 to the frame-driven thrust handler at 0x6A5C00. */
    private static boolean sandorThrust(AttackScene scene, long bodyEnd) throws IOException {
        double contact = scene.x + 100;
        scene.target(contact, bodyEnd);
        return true;
    }

    /** 0x6A7690 attaches the HP restore at frame 4. 0x6A8CE0 starts the taunt on entry. */
    private static boolean sandorSupport(AttackScene scene, Move move, List<GameScript.Field> fields,
                                        ResourceScript.Animation body) throws IOException {
        long cue = move == Move.SANDOR_TAUNT ? scene.start : frameTime(body, scene.start, 4);
        if (move == Move.SANDOR_HEAL) {
            scene.particle("common/commoneffect/particle/healparticle.ptl", cue, 500, scene.x, 0, 0, null);
        } else {
            String effect = "character/mage/effect/animation/provocationdoll";
            double scale = value(fields, 4, 200) / 160.0;
            long end = cue + Math.max(1, Math.min(30000, value(fields, 5, 10000)));
            scene.scaled(effect + "symbol1.ani", 279, cue, end, scale);
            scene.scaled(effect + "symbol2.ani", 280, cue, end, scale);
            long open = scene.scaled(effect + "areastart.ani", 281, cue, 0, scale);
            scene.scaled(effect + "arealoop.ani", 281, open, end, scale);
        }
        return true;
    }

    /** State 8 index 1 spawns the group on entry; 0x5C4470 spawns its four needles on its first update. */
    private static boolean luisIce(AttackScene scene) throws IOException {
        double target = scene.x + 150;
        long end = scene.start;
        for (int id = 30364; id <= 30368; id++) {
            var ice = Act1SkillSequence.objectMotions(scene.lookup, id);
            if (ice.isEmpty()) return false;
            end = Math.max(end, scene.once(ice.get(0), 3600 + id - 30364, scene.start, target, 0, true));
        }
        scene.once(COMMON + "animation/iceniddleglow.ani", 3605, scene.start, target, 0, false);
        scene.target(target, end);
        return true;
    }

    /** 0x6CA8A0 launches object 30360 on frame 3. Its terminal explosion is object 30361. */
    private static boolean luisFire(AttackScene scene, List<GameScript.Field> monsterFields,
                                    ResourceScript.Animation body) throws IOException {
        var fire = Act1SkillSequence.objectMotions(scene.lookup, 30360);
        var blast = Act1SkillSequence.objectMotions(scene.lookup, 30361);
        var flight = scene.projectile(LUIS + "particle/energyball.ptl");
        if (fire.size() < 2 || blast.isEmpty() || flight == null) return false;
        long release = frameTime(body, scene.start, 3);
        double target = scene.x + 230;
        var data = GameScript.fields(scene.lookup.read(GameEffectRecipes.passiveObject(scene.lookup, 30360)));
        int first = value(data, 0, 160);
        int last = value(data, 1, 240);
        int skillLevel = value(monsterFields, 0, 20);
        double scale = Math.max(.1, Math.min(10, (first + (last - (double) first) * skillLevel / 25.0) / 100));
        double contactX = target - 20 * scale;
        long travel = Math.min(flight.life, Math.max(1, Math.round(1000 * (contactX - scene.x - 70) / flight.speed)));
        long hit = release + travel;
        var motion = new SkillMotion(release, travel, flight.speed, 0, 0, 0, 63);
        long initial = release + duration(readAnimation(fire.get(1), scene.lookup));
        scene.layer(fire.get(1), 3601, release, initial, scene.x + 70, -63, null, false, false, scale, false);
        scene.layer(fire.get(0), 3600, release, hit, scene.x + 70, 0, motion, true, true, scale, false);
        double impactX = scene.x + 70 + motion.offsetAt(hit);
        long end = scene.once(blast.get(0), 3600, hit, impactX, -63, true);
        for (String particle : scene.objectParticles(30361)) {
            scene.particle(particle, hit, end - hit, impactX, 0, 63, null);
        }
        scene.target(target, scene.end);
        return true;
    }

    /** State 13/5 calls 0x6CA710; the stone emits its impact object below Z=30 in 0x5C5010. */
    private static boolean luisMeteor(AttackScene scene, Move move) throws IOException {
        var meteor = Act1SkillSequence.objectMotions(scene.lookup, 30362);
        var blast = Act1SkillSequence.objectMotions(scene.lookup, 30363);
        String path = scene.lookup.read(COMMON + "particle/shootingstarpath.ptl");
        if (meteor.size() < 2 || blast.isEmpty() || path == null) return false;
        var trajectory = ParticleScript.parse(path);
        var horizontal = trajectory.axes().get(0);
        var vertical = trajectory.axes().get(2);
        int targets = move == Move.LUIS_METEOR_DEATH ? 2 : 1;
        for (int i = 0; i < targets; i++) {
            boolean left = i == 1;
            // Two stable choices within the authored random trajectory range.
            double speed = left ? horizontal.start() : horizontal.end();
            double drop = Math.abs(left ? vertical.start() : vertical.end());
            if (speed <= 0 || drop <= 0) return false;
            long travel = (long)Math.ceil(370_000 / drop);
            long impact = scene.start + travel;
            double target = scene.x + (left ? -180 : 180);
            double origin = target + (left ? 500 : -500);
            var fall = new SkillMotion(scene.start, travel, left ? -speed : speed, 0, -drop, 0, 400);
            scene.layer(meteor.get(0), 3600, scene.start, impact, origin, 0, fall, true, true, 1, left);
            scene.layer(meteor.get(1), 3601, scene.start, impact, origin, 0, fall, false, true, 1, left);
            scene.particle(COMMON + "particle/shootingstarfire.ptl", scene.start, travel, origin, 0, 0, fall,
                    left, false);
            double impactX = origin + fall.offsetAt(impact);
            long end = scene.once(blast.get(0), 3600, impact, impactX, 0, true);
            for (String name : List.of("shootingstarexp", "shootingstarexpsmoke", "shootingstarexpetc")) {
                scene.particle(COMMON + "particle/" + name + ".ptl", impact, end - impact, impactX, 0, 0, null);
            }
            scene.target(target, end, left);
        }
        return true;
    }

    /** 0x6723D0 calls 0x6721E0 at frame 2; the root disables contact on frame 2 of its own ANI. */
    private static boolean auxoRoot(AttackScene scene, ResourceScript.Animation body) throws IOException {
        var root = Act1SkillSequence.objectMotions(scene.lookup, 30351);
        if (root.isEmpty()) return false;
        long release = frameTime(body, scene.start, 2);
        double target = scene.x + 190;
        scene.cutoff(root.get(0), 3600, release, target, 0, 0, 2);
        scene.target(target, scene.end);
        return true;
    }

    /** 0x6723D0 emits confusion at frame 3 and the moving poison object at frame 4. */
    private static boolean auxoPoison(AttackScene scene, List<GameScript.Field> fields, ResourceScript.Animation body,
                                     long bodyEnd) throws IOException {
        var cloud = Act1SkillSequence.objectMotions(scene.lookup, 48021);
        var bug = Act1SkillSequence.objectMotions(scene.lookup, value(fields, 3, 30350));
        var flight = scene.projectile(AUXO + "particle/poisonbug.ptl");
        if (cloud.isEmpty() || bug.isEmpty() || flight == null) return false;
        long gas = frameTime(body, scene.start, 3);
        long release = frameTime(body, scene.start, 4);
        double origin = scene.x + 100;
        long gasEnd = scene.once(cloud.get(0), 3600, gas, origin, .29, true);
        for (String particle : scene.objectParticles(48021)) {
            scene.particle(particle, gas, gasEnd - gas, origin, .29, 0, null, false, true);
        }
        String path = "passiveobject/" + AUXO + "particle/poisonbug.ptl";
        var motion = new SkillMotion(release, flight.life, flight.speed, 0, 0, 0, 50);
        long end = release + flight.life;
        scene.layer(bug.get(0), 3600, release, end, scene.x, 0, motion, true, true);
        for (long tick = release + 50; tick < end; tick += 50) {
            scene.particle(path, tick, 1, scene.x + motion.offsetAt(tick), 0, 50, null, false, true);
        }
        scene.target(scene.x + 105, Math.max(bodyEnd, scene.end));
        return true;
    }

    private static int value(List<GameScript.Field> fields, int index, int fallback) {
        var data = GameScript.values(fields, "int data");
        return index >= 0 && index < data.size() ? GameScript.integer(data.get(index), fallback) : fallback;
    }

    private static String motion(String owner, List<GameScript.Field> fields, String tag, int index,
                                 GameScript.Lookup lookup) throws IOException {
        var refs = GameScript.values(fields, tag);
        return index < refs.size() && !refs.get(index).isEmpty() ? GameScript.resolve(owner, refs.get(index), lookup) : null;
    }

    private static ResourceScript.Animation readAnimation(String path, GameScript.Lookup lookup) throws IOException {
        String text = path == null ? null : lookup.read(path);
        return text == null ? new ResourceScript.Animation(false, List.of(), List.of()) : ResourceScript.animation(text);
    }

    private static long duration(ResourceScript.Animation animation) {
        return new AnimationTimeline(animation).duration();
    }

    private static long frameTime(ResourceScript.Animation animation, long start, int index) {
        return start + Act1SkillSequence.duration(animation.frames(), index, null);
    }

    private record Projectile(double speed, double rise, double gravity, long life) {}

    private static final class AttackScene {
        final GameScript.Lookup lookup;
        final long start;
        final double x;
        final String label;
        final List<Visual> visuals = new ArrayList<>();
        final List<SkillParticleEmitter> emitters = new ArrayList<>();
        long end;

        AttackScene(GameScript.Lookup lookup, long start, double x, String label) {
            this.lookup = lookup;
            this.start = start;
            this.x = x;
            this.label = label;
            this.end = start;
        }

        void layer(String path, int layer, long from, long to, double atX, double y, SkillMotion motion,
                   boolean attack, boolean loop) throws IOException {
            layer(path, layer, from, to, atX, y, motion, attack, loop, 1, false);
        }

        void layer(String path, int layer, long from, long to, double atX, double y, SkillMotion motion,
                   boolean attack, boolean loop, double scale, boolean reverse) throws IOException {
            var animation = readAnimation(path, lookup);
            if (animation.frames().isEmpty() || to <= from) return;
            var style = new VisualStyle(null, 0, List.of(), Map.of(), reverse, loop ? duration(animation) : 0);
            var visual = new Visual(path, label, layer, from, to, atX, y, motion, scale, true, style);
            visuals.add(attack ? visual.asAlliedAttack() : visual.withoutAttack());
            end = Math.max(end, to);
        }

        long once(String path, int layer, long from, double atX, double y, boolean attack) throws IOException {
            long to = from + duration(readAnimation(path, lookup));
            layer(path, layer, from, to, atX, y, null, attack, false);
            return to;
        }

        void cutoff(String path, int layer, long from, double atX, double y, int first, int last)
                throws IOException {
            var animation = readAnimation(path, lookup);
            if (animation.frames().isEmpty()) return;
            var frames = new ArrayList<ResourceScript.Frame>();
            for (int i = 0; i < animation.frames().size(); i++) {
                var f = animation.frames().get(i);
                frames.add(i < first || i >= last ? f.withoutAttack() : f);
            }
            var style = new VisualStyle(new ResourceScript.Animation(animation.loop(), List.copyOf(frames), animation.warnings()));
            long to = from + duration(animation);
            visuals.add(new Visual(path, label, layer, from, to, atX, y, null, 1, true, style).asAlliedAttack());
            end = Math.max(end, to);
        }

        long scaled(String path, int layer, long from, long until, double scale) throws IOException {
            long life = duration(readAnimation(path, lookup));
            long to = until > 0 ? until : from + life;
            if (life == 0 || to <= from) return from;
            var style = new VisualStyle(null, 0, List.of(), Map.of(), false, until > 0 ? life : 0);
            visuals.add(new Visual(path, label, layer, from, to, x, 0, null, scale, true, style).withoutAttack());
            end = Math.max(end, to);
            return to;
        }

        void target(double atX, long until) throws IOException {
            target(atX, until, false);
        }

        void target(double atX, long until, boolean left) throws IOException {
            String path = Act1SwordAsuraSupplement.GOBLIN + "stay.ani";
            long life = duration(readAnimation(path, lookup));
            if (life == 0 || until <= start) return;
            var style = new VisualStyle(null, 0, List.of(), Map.of(), !left, life);
            visuals.add(new Visual(path, "攻击目标 · 哥布林", BODY_LAYER, start, until, atX, 0, null, 1, true, style));
        }

        Projectile projectile(String path) throws IOException {
            String text = lookup.read(path);
            if (text == null) return null;
            var script = ParticleScript.parse(text);
            double speed = script.axes().get(0).start();
            long life = Math.min(10000, Math.round(script.life()));
            return speed > 0 && life > 0 ? new Projectile(speed, script.axes().get(2).start(), script.gravity(), life) : null;
        }

        List<String> objectParticles(int id) throws IOException {
            String object = GameEffectRecipes.passiveObject(lookup, id);
            if (object == null) return List.of();
            var result = new ArrayList<String>();
            for (String reference : GameScript.values(GameScript.fields(lookup.read(object)), "string data")) {
                String particle = GameScript.resolve(object, reference, lookup);
                if (particle != null && particle.endsWith(".ptl")) result.add(particle);
            }
            return result;
        }

        void particle(String path, long from, long time, double atX, double y, double z, SkillMotion motion)
                throws IOException {
            particle(path, from, time, atX, y, z, motion, false, false);
        }

        void particle(String path, long from, long time, double atX, double y, double z, SkillMotion motion,
                      boolean reverse, boolean decorative) throws IOException {
            String text = path == null ? null : lookup.read(path);
            if (text == null) return;
            var script = ParticleScript.parse(text);
            var emitter = new SkillParticleEmitter(path, label, from, time, 0, atX, y, z,
                    Double.NaN, 1, true, 1, reverse, motion);
            emitters.add(decorative ? emitter.withoutAttack() : emitter);
            end = Math.max(end, from + time + SkillParticleEmitter.tail(script));
        }

        void equipment(int monsterId, String tag, int index, long until, SkillMotion movement, boolean attack)
                throws IOException {
            String folder;
            List<String> parts;
            switch (monsterId) {
                case 900 -> {
                    folder = "equipment/monster/goblin/";
                    parts = List.of("acc9", "acc9-2", "acc9-3", "bcc9", "hcc9", "weapon9");
                }
                case 901 -> {
                    folder = "equipment/monster/soldier/sandorexpeller/sandorexpeller";
                    parts = List.of("leg", "head", "rightarm", "leftarm", "headend", "glow");
                }
                case 902 -> {
                    folder = "equipment/monster/soceress/luis_";
                    parts = List.of("hair01", "cloth01", "weapon01", "acc01", "weapon02");
                }
                default -> { return; }
            }
            for (String part : parts) {
                String equipment = folder + part + ".equ";
                int order = 0;
                for (var field : GameScript.fields(lookup.read(equipment))) {
                    if (field.tag().equals("layer")) order = GameScript.integer(field.first(), 0);
                    if (!field.tag().equals(tag) || field.values().isEmpty()) continue;
                    int motionIndex = index == 5 && monsterId == 901 ? 0 : index;
                    if (motionIndex >= field.values().size() || field.values().get(motionIndex).isEmpty()) continue;
                    String path = GameScript.resolve(equipment, field.values().get(motionIndex), lookup);
                    layer(path, BODY_LAYER + Math.max(-1000, Math.min(1000, order)), start, until, x, 0,
                            movement, attack && part.equals("leftarm"), tag.equals("waiting motion"));
                }
            }
        }
    }
}
