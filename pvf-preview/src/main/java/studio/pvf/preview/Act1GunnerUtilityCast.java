package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** ACT1 active Gunner skills that modify a normal attack, a firing stance or a summon. */
final class Act1GunnerUtilityCast {
    private static final String GUNNER = "character/gunner/";
    private static final String CHARACTER = GUNNER + "gunner.chr";
    private static final String FX = GUNNER + "effect/animation/";
    private static final String WAIT = "waiting motion";
    private static final String FAST_SHOOT = "skill/gunner/fastshoot.skl";
    private static final String WALK = "skill/gunner/walkshoot.skl";
    private static final String TURN = "skill/gunner/walkshootturning.skl";
    private static final String DELAYED_EZ8 = "skill/gunner/delayedrobotez8.skl";
    private static final String FAST_DOWN = "fastshoot:down";
    private static final String BULLET_WALL = "gunner-bullet:wall";
    private static final String WALL_ART = FX + "hiteffect/collisionwall.ani";
    private static final String EZ8_DIGITS = "monster/gunner/ez8/ez8_num.img";

    private Act1GunnerUtilityCast() {}

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup, Map<String, String> actions,
                                  int level, String choice, String weaponPath) throws IOException {
        int id = switch (path) {
            case FAST_SHOOT -> 11;
            case TURN -> 32;
            case DELAYED_EZ8 -> 51;
            default -> 0;
        };
        boolean down = id == 11 && choice.equals(FAST_DOWN);
        boolean wall = isWallChoice(path, choice);
        if (id == 0 || !choice.isEmpty() && !down && !wall || !Act1SkillSequence.matches(path, "gunner", id, lookup)
                || !actions.containsKey(WAIT)) return null;
        var fields = GameScript.fields(text);
        String type = GameScript.flag(GameScript.value(fields, "type"));
        if (!type.isEmpty() && !type.equals("active")) return null;
        return switch (id) {
            case 11 -> fastShoot(fields, lookup, actions, level, weaponPath, down, wall);
            case 32 -> turn(lookup, actions, weaponPath, wall);
            case 51 -> delayedEz8(fields, lookup, actions);
            default -> null;
        };
    }

    static List<GameEffectRecipes.Effect> choices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        int skill = wallSkill(path);
        if (!act1 || skill == 0 || !Act1SkillSequence.matches(path, "gunner", skill, lookup)) return List.of();
        var choices = new ArrayList<GameEffectRecipes.Effect>();
        if (skill == 11) choices.add(new GameEffectRecipes.Effect(FAST_DOWN, "快速拔枪 · 向下射击", "", path,
                "演示同一拔枪技能的下向枪口、弹道和光效"));
        if (lookup.read(WALL_ART) != null) {
            choices.add(new GameEffectRecipes.Effect(BULLET_WALL, "左轮弹撞墙", "", path, "演示一发普通左轮弹触墙后的原版冲击光"));
        }
        return List.copyOf(choices);
    }

    static boolean isWallChoice(String path, String choice) {
        return choice.equals(BULLET_WALL) && wallSkill(path) != 0;
    }

    private static int wallSkill(String path) {
        return switch (path) {
            case FAST_SHOOT -> 11;
            case WALK -> 15;
            case TURN -> 32;
            case "skill/gunner/headshot.skl" -> 5;
            case "skill/gunner/turningshoot.skl" -> 20;
            case "skill/gunner/liftshot.skl" -> 47;
            default -> 0;
        };
    }

    /** Gunner state 8 uses the normal two-handed attack, with skill 11's draw-speed bonus and overlays. */
    private static Act1SkillSequence fastShoot(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                              Map<String, String> actions, int level, String weapon, boolean down,
                                              boolean wall) throws IOException {
        var weaponFields = weaponFields(lookup, weapon);
        var gun = gun(lookup, down, weaponFields);
        String character = lookup.read(CHARACTER);
        if (gun == null || character == null) return null;
        int attackSpeed = Act1GunnerPrecisionCast.number(GameScript.value(GameScript.fields(character), "attack speed"))
                + GameScript.integer(GameScript.value(weaponFields, "attack speed"), 0);
        if (attackSpeed < 100 || attackSpeed > 5000) return null;
        int draw = Math.max(1, gun.draw() * attackSpeed / 1000)
                + Act1SkillSequence.levelValue(fields, level, 0, 100, 0, 10000);
        int reaction = Math.max(1, gun.reaction() * 1000 / attackSpeed);
        long after = gun.after() * 1000L / attackSpeed;
        int reload = Math.max(1, gun.reload() * attackSpeed / 1000);
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        var particle = bullet(lookup, gun);
        if (particle == null) return null;
        String direction = down ? "down" : "up";
        long next = 0;
        long finish = 0;
        for (int hand = 0; hand < (wall ? 1 : 2); hand++) {
            String prepare = "attack motion:" + hand;
            String fire = "etc motion:" + (hand * 2 + (down ? 1 : 0));
            var ready = Act1SkillSequence.frames(CHARACTER, actions.get(prepare), lookup);
            var firing = Act1SkillSequence.frames(CHARACTER, actions.get(fire), lookup);
            int first = hand == 0 ? 0 : 2;
            int hold = ready.size() - 1;
            if (hold <= first || ready.get(hold).delay() < 10000 || firing.size() < 2) return null;
            long raw = Act1SkillSequence.duration(ready.subList(first, hold), hold - first, null);
            long aimed = next + Math.max(1, (raw * 1000 + draw - 1) / draw);
            long fired = aimed + reaction;
            poses.add(new Pose(prepare, next, aimed, null, true, null, first, hold));
            long recovery = after + (hand == 0 ? Math.max(125, 250000 / reload) : 100000L / reload);
            next = fired + Math.max(1, recovery);
            poses.add(new Pose(fire, aimed, next, reaction, false, null));
            double dx = hand == 0 ? 0 : down ? 34 : 38;
            double dz = hand == 0 ? 0 : down ? -5 : -7;
            long flashEnd = add(visuals, lookup, FX + "fastshoot" + direction + ".ani",
                    "快速拔枪 · " + (hand == 0 ? "第一枪拔枪光" : "第二枪拔枪光"), 3501,
                    aimed, hand == 0 ? 0 : 32, hand == 0 ? 0 : 8 * .29, 1, false);
            if (flashEnd == aimed) return null;
            long muzzleEnd = addMuzzle(visuals, lookup, gun, down, fired, particle.position().x() + dx,
                    particle.position().y() * .29 - particle.position().z() - dz, false);
            if (muzzleEnd == fired) return null;
            double x = (down ? 44 : 47) + dx;
            double z = (down ? 90 : 119) + dz;
            long bulletEnd = wall ? wallBullet(visuals, emitters, lookup, gun, particle, fired, x, 0, z, false, "快速拔枪")
                    : addBullet(emitters, lookup, gun, particle, fired, x, 0, z, false, "快速拔枪", Long.MAX_VALUE);
            finish = Math.max(finish, Math.max(flashEnd, Math.max(muzzleEnd, bulletEnd)));
            if (hand == 1) {
                finish = Math.max(finish, bulletImpact(visuals, lookup, gun, particle, down, fired,
                        x, 0, z, false, "快速拔枪"));
            }
        }
        finish = Math.max(finish, next);
        if (finish > next) poses.add(new Pose(WAIT, next, finish));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters),
                wall ? "快速拔枪演示第一枪撞墙，墙面位置为固定演示取样。"
                        : "快速拔枪演示连续两次普攻输入。两手分别使用原版枪口和拔枪光，当前等级的拔枪加速参与计时。"
                        + "左轮枪、自动手枪和步枪使用普通子弹命中光，目标在弹道上取样。");
    }

    /** State 23 checks skill 32 while movement shooting is already active. */
    private static Act1SkillSequence turn(GameScript.Lookup lookup, Map<String, String> actions,
                                          String weapon, boolean wall) throws IOException {
        var gun = gun(lookup, false, weaponFields(lookup, weapon));
        if (gun == null) return null;
        var particle = bullet(lookup, gun);
        if (particle == null) return null;
        String ready = "etc motion:23", forward = "etc motion:24", shoot = "etc motion:26";
        for (String action : List.of(ready, forward, shoot)) {
            if (Act1SkillSequence.frames(CHARACTER, actions.get(action), lookup).isEmpty()) return null;
        }
        long walkAt = 200, turnAt = 600, fireAt = 800;
        var advance = new SkillMotion(walkAt, turnAt - walkAt, 60, 0);
        double turnedX = advance.offsetAt(turnAt);
        var stop = new SkillMotion(turnAt, 0, 0, 0, turnedX);
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        long muzzleEnd = addMuzzle(visuals, lookup, gun, false, fireAt, turnedX - 47, -119, true);
        if (muzzleEnd == fireAt) return null;
        double x = turnedX - 47, y = -2 * .29;
        long shotEnd = wall ? wallBullet(visuals, emitters, lookup, gun, particle, fireAt, x, y, 117, true, "移动射击转身")
                : Math.max(addBullet(emitters, lookup, gun, particle, fireAt, x, y, 117, true, "移动射击转身", Long.MAX_VALUE),
                bulletImpact(visuals, lookup, gun, particle, false, fireAt, x, y, 117, true, "移动射击转身"));
        long end = Math.max(fireAt + 400, Math.max(muzzleEnd, shotEnd));
        return new Act1SkillSequence(List.of(
                new Pose(ready, 0, walkAt), new Pose(forward, walkAt, turnAt, null, false, advance),
                new Pose(ready, turnAt, fireAt + 100, null, false, stop, 0, Integer.MAX_VALUE, true),
                new Pose(shoot, fireAt + 100, fireAt + 300, null, false, stop, 0, Integer.MAX_VALUE, true),
                new Pose(ready, fireAt + 300, end, null, false, stop, 0, Integer.MAX_VALUE, true)),
                List.copyOf(visuals), List.copyOf(emitters),
                wall ? "移动射击转身后向反方向开火，墙面位置为固定演示取样。"
                        : "移动射击转身演示先进入移动射击、向前走、反转面向，再向反方向开火。移动与攻击输入的时间为固定演示取样。");
    }

    /** 0x66D690 applies skill 51's extra time to the ordinary Ez-8 countdown. */
    private static Act1SkillSequence delayedEz8(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                               Map<String, String> actions) throws IOException {
        String base = "skill/gunner/robotez8.skl";
        if (!Act1SkillSequence.matches(base, "gunner", 28, lookup)
                || Act1SkillSequence.frames(CHARACTER, actions.get("etc motion:32"), lookup).isEmpty()) return null;
        String skillSource = lookup.read(base);
        if (skillSource == null) return null;
        var baseFields = GameScript.fields(skillSource);
        long cast = Act1SkillSequence.castingTime(baseFields, 1, 400);
        return Act1GunnerMechanicalSupplement.ez8(baseFields, lookup, 1, cast,
                Act1SkillSequence.staticValue(fields, 0, 10000), "Ez-8延迟自爆", true);
    }
    /** 0x66D380 draws two seconds digits, a separator and two hundredths digits, using the Ez-8 atlas. */
    static void addCountdown(List<Visual> visuals, long start, int timer, double x, String label) {
        int countdown = timer - 500;
        int[] offsets = {-31, -16, -1, 6, 18};
        for (int column = 0; column < offsets.length; column++) {
            int slot = column;
            int dx = offsets[column];
            var frames = new ArrayList<ResourceScript.Frame>();
            if (slot < 3) {
                frames.add(digit(countdownDigit(countdown, slot), dx, 1));
                for (int elapsed = 1; elapsed <= countdown; ) {
                    int remaining = countdown - elapsed;
                    int place = slot == 0 ? 10000 : 1000;
                    int delay = Math.min(remaining + 1, slot == 2 ? countdown : remaining % place + 1);
                    frames.add(digit(countdownDigit(remaining, slot), dx, delay));
                    elapsed += delay;
                }
                addDigits(visuals, frames, start, start + countdown + 1, x, false, label);
            } else {
                addDigits(visuals, List.of(digit(countdownDigit(countdown, slot), dx, 1)),
                        start, start + 1, x, false, label);
                int period = slot == 3 ? 1000 : 100;
                int step = period / 10;
                for (int elapsed = 0; elapsed < period; ) {
                    int remaining = Math.floorMod(countdown - 1 - elapsed, period);
                    int delay = Math.min(period - elapsed, remaining % step + 1);
                    frames.add(digit(countdownDigit(remaining, slot), dx, delay));
                    elapsed += delay;
                }
                addDigits(visuals, frames, start + 1, start + countdown + 1, x, true, label);
            }
            frames = new ArrayList<>();
            for (int remaining = 499; remaining > 0; ) {
                int delay = remaining % 150 + 1;
                if (remaining < delay) delay = remaining;
                frames.add(digit(remaining / 150 % 2 == 1 ? -1 : countdownDigit(0, slot), dx, delay));
                remaining -= delay;
            }
            addDigits(visuals, frames, start + countdown + 1, start + timer, x, false, label);
        }
    }

    private static int countdownDigit(int time, int slot) {
        return switch (slot) {
            case 0 -> time / 10000 % 10;
            case 1 -> time / 1000 % 10;
            case 2 -> 10;
            case 3 -> time / 100 % 10 + 11;
            default -> time / 10 % 10 + 11;
        };
    }

    private static ResourceScript.Frame digit(int index, int x, int delay) {
        return ResourceScript.Frame.still(index < 0 ? "" : EZ8_DIGITS, Math.max(0, index)).translated(x, 0).withDelay(delay);
    }

    private static void addDigits(List<Visual> visuals, List<ResourceScript.Frame> frames,
                                  long start, long end, double x, boolean loop, String label) {
        var animation = new ResourceScript.Animation(loop, List.copyOf(frames), List.of());
        visuals.add(new Visual(EZ8_DIGITS, label + " · 倒计时", 3503, start, end, x, -63,
                null, 1, false, new VisualStyle(animation)));
    }

    private static List<GameScript.Field> weaponFields(GameScript.Lookup lookup, String weapon) throws IOException {
        return weapon == null || weapon.isEmpty() ? List.of() : GameScript.fields(lookup.read(GameScript.normalize(weapon)));
    }

    private static Act1GunnerPrecisionCast.Gun gun(GameScript.Lookup lookup, boolean down,
                                                   List<GameScript.Field> weapon) throws IOException {
        int type = GameScript.integer(GameScript.value(weapon, "sub type"), 0);
        return Act1GunnerPrecisionCast.gun(lookup, type, down, weapon);
    }

    private static ParticleScript bullet(GameScript.Lookup lookup, Act1GunnerPrecisionCast.Gun gun) throws IOException {
        String source = lookup.read(gun.particle());
        if (source == null) return null;
        var particle = ParticleScript.parse(source);
        return particle.passive() && particle.frequency() == -1 && particle.maximum() == 1
                && particle.objects().size() == 1 ? particle : null;
    }

    private static long addMuzzle(List<Visual> visuals, GameScript.Lookup lookup, Act1GunnerPrecisionCast.Gun gun,
                                  boolean down, long start, double x, double y, boolean reverse) throws IOException {
        String path = Act1GunnerPrecisionCast.muzzle(gun.type(), down);
        return add(visuals, lookup, path, reverse ? "移动射击转身 · 反向枪口" : "快速拔枪 · 枪口",
                3502, start, x, y, 1, reverse, List.of(0, 0));
    }

    private static long addBullet(List<SkillParticleEmitter> emitters, GameScript.Lookup lookup,
                                  Act1GunnerPrecisionCast.Gun gun, ParticleScript particle, long start,
                                  double x, double y, double z, boolean reverse, String label, long maxDuration) throws IOException {
        var motions = Act1SkillSequence.objectMotions(lookup, particle.objects().get(0).index());
        if (motions.isEmpty()) return start;
        boolean hasContent = ResourceScript.animation(lookup.read(motions.get(0))).frames().stream()
                .anyMatch(frame -> !frame.image().isEmpty() || !frame.combat().attack().isEmpty());
        if (!hasContent) return start;
        emitters.add(new SkillParticleEmitter(gun.particle(), label + (reverse ? " · 反向弹道" : " · 弹道"),
                start, 1, 0, x, y, z, Double.NaN, 1, true, 1, reverse, null, "", maxDuration));
        return start + Math.min(1 + SkillParticleEmitter.tail(particle), maxDuration);
    }

    /** CNGunnerBullet chooses the common up/down collision ANI for weapon subtypes 0–2 in 0x5A64A0. */
    private static long bulletImpact(List<Visual> visuals, GameScript.Lookup lookup, Act1GunnerPrecisionCast.Gun gun,
                                     ParticleScript particle, boolean down, long fired, double x, double y,
                                     double z, boolean reverse, String label) throws IOException {
        if (gun.type() > 2) return fired;
        long travel = impactTravel(particle);
        var contact = contact(gun, particle, travel, x, y, z, reverse);
        if (contact == null) return fired;
        long hit = fired + travel;
        long end = add(visuals, lookup, FX + "hiteffect/" + (down ? "down" : "up") + "default.ani",
                label + " · 普通子弹命中光", 3552, hit, contact.x(), contact.y() - contact.z(), 1, reverse);
        if (end == hit) return fired;
        // The preview places the Goblin in the projectile's path, above the ground for an upward shot.
        double height = Math.max(0, contact.z() - 32);
        var targetMotion = new SkillMotion(0, 0, 0, 0, 0, 0, height);
        bulletTarget(visuals, lookup, "stay", label + " · 演示目标", 0, hit, contact.x(), contact.y(), targetMotion, reverse);
        bulletTarget(visuals, lookup, "damage1", label + " · 子弹受击", hit, end, contact.x(), contact.y(), targetMotion, reverse);
        return end;
    }

    static long walkingWallBullet(List<Visual> visuals, List<SkillParticleEmitter> emitters, GameScript.Lookup lookup,
                                  String weapon, long fired, double x, double y, double z) throws IOException {
        var gun = gun(lookup, false, weaponFields(lookup, weapon));
        if (gun == null) return fired;
        var particle = bullet(lookup, gun);
        return particle == null ? fired : wallBullet(visuals, emitters, lookup, gun, particle, fired, x, y, z, false, "移动射击");
    }

    /** 0x5A5970 records a non-ground contact and 0x5A78A0 replaces the bullet with CollisionWall. */
    static long wallBullet(List<Visual> visuals, List<SkillParticleEmitter> emitters, GameScript.Lookup lookup,
                           Act1GunnerPrecisionCast.Gun gun, ParticleScript particle, long fired,
                           double x, double y, double z, boolean reverse, String label) throws IOException {
        long travel = impactTravel(particle);
        var contact = contact(gun, particle, travel, x, y, z, reverse);
        boolean supported = gun.type() <= 2 && particle.objects().get(0).index() >= 22001 && particle.objects().get(0).index() <= 22015;
        if (supported && contact != null && contact.z() > 0) {
            long hit = fired + travel;
            long impactEnd = add(visuals, lookup, WALL_ART, label + " · 子弹撞墙", 3552,
                    hit, contact.x(), contact.y() - contact.z(), 1, reverse);
            if (impactEnd > hit) {
                addBullet(emitters, lookup, gun, particle, fired, x, y, z, reverse, label, travel);
                return impactEnd;
            }
        }
        return addBullet(emitters, lookup, gun, particle, fired, x, y, z, reverse, label, Long.MAX_VALUE);
    }

    private static ParticleTimeline.Particle contact(Act1GunnerPrecisionCast.Gun gun, ParticleScript particle,
                                                      long travel, double x, double y, double z, boolean reverse) {
        var trail = new ParticleTimeline(particle.previewEmission(1, 0), gun.particle().hashCode(), 0);
        var sample = trail.sample(travel, x, y, z, reverse).particles();
        return sample.isEmpty() ? null : sample.get(0);
    }

    private static long impactTravel(ParticleScript particle) {
        return Math.min(90, Math.max(1, Math.round(particle.life() / 2)));
    }

    private static void bulletTarget(List<Visual> visuals, GameScript.Lookup lookup, String animation, String label,
                                     long start, long end, double x, double y, SkillMotion motion, boolean reverse) throws IOException {
        String path = Act1SwordAsuraSupplement.GOBLIN + animation + ".ani";
        if (lookup.read(path) == null) return;
        var style = new VisualStyle(null, 0, List.of(), Map.of(), !reverse);
        visuals.add(new Visual(path, label, 3550, start, end, x, y, motion, 1, true, style));
    }

    private static long add(List<Visual> visuals, GameScript.Lookup lookup, String path, String label, int layer,
                            long start, double x, double y, double scale, boolean reverse) throws IOException {
        return add(visuals, lookup, path, label, layer, start, x, y, scale, reverse, List.of());
    }

    private static long add(List<Visual> visuals, GameScript.Lookup lookup, String path, String label, int layer,
                            long start, double x, double y, double scale, boolean reverse,
                            List<Integer> variations) throws IOException {
        String source = lookup.read(path);
        if (source == null) return start;
        long end = start + new AnimationTimeline(ResourceScript.animation(source)).duration();
        var style = new VisualStyle(null, 0, List.of(), Map.of(), reverse);
        if (end > start) visuals.add(new Visual(path, label, layer, start, end, x, y, null, scale, true, style, variations));
        return end;
    }
}
