package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import studio.pvf.preview.Act1SkillSequence.Visual;

final class Act1RobotAttack {
    static final String ROBOT = "monster/gunner/robot/";
    private static final String PASSIVE = "passiveobject/character/gunner/";
    private static final String COMMON = "passiveobject/common/";
    static final String QUAKE = "passiveobject/common/animation/earthquake.ani";

    private Act1RobotAttack() {}

    enum Robot {
        RX78(22214, "rx78explosion", "rx78_boom", 30),
        EZ8(22215, "ez8explosion", "ez8_boom", 0),
        EXS(22216, "exsexplosion", "exs_boom", -1),
        TEMPESTER(22226, "tempesterexplosion", "tempesterboom", -1),
        MECHADROP(22227, "mechadropexplosion", "rx78_boom", 30),
        MECHADROP_FIRE(22222, "firerx78explosion", "rx78_boom", 30, "fireexplosion");

        final int id;
        final String object;
        final String debris;
        final int quakeHeight;
        final String explosion;

        Robot(int id, String object, String debris, int quakeHeight) {
            this(id, object, debris, quakeHeight, "noneexplosion");
        }

        Robot(int id, String object, String debris, int quakeHeight, String explosion) {
            this.id = id;
            this.object = object;
            this.debris = debris;
            this.quakeHeight = quakeHeight;
            this.explosion = explosion;
        }
    }

    static long animationDuration(String path, GameScript.Lookup lookup) throws IOException {
        if (path == null) return 0;
        String text = lookup.read(path);
        return text == null ? 0 : new AnimationTimeline(ResourceScript.animation(text)).duration();
    }

    static String passiveMotion(GameScript.Lookup lookup, int id, String stem) throws IOException {
        String object = PASSIVE + stem + ".obj";
        if (!object.equals(GameEffectRecipes.passiveObject(lookup, id))) return null;
        String source = lookup.read(object);
        if (source == null) return null;
        String motion = GameScript.resolve(object, GameScript.value(GameScript.fields(source), "basic motion"), lookup);
        return animationDuration(motion, lookup) > 0 ? motion : null;
    }

    static Blast blast(GameScript.Lookup lookup, Robot robot) throws IOException {
        String object = PASSIVE + robot.object + ".obj";
        if (!object.equals(GameEffectRecipes.passiveObject(lookup, robot.id))) return null;
        String source = lookup.read(object);
        if (source == null) return null;
        var fields = GameScript.fields(source);
        String root = COMMON + "animation/" + robot.explosion + ".ani";
        if (!root.equals(GameScript.resolve(object, GameScript.value(fields, "basic motion"), lookup))) return null;
        long life = animationDuration(root, lookup);
        long quake = robot.quakeHeight < 0 ? 0 : animationDuration(QUAKE, lookup);
        if (life < 1 || life > 3000 || robot.quakeHeight >= 0 && quake < 1) return null;
        var refs = GameScript.values(fields, "string data");
        if (refs.size() != 4) return null;
        var particles = new ArrayList<Particle>();
        String debris = ROBOT + "particle/" + robot.debris + ".ptl";
        if (!addParticle(particles, lookup, debris, 1)) return null;
        for (int i = 0; i < refs.size(); i++) {
            String particle = GameScript.resolve(object, refs.get(i), lookup);
            if (!(COMMON + "particle/" + robot.explosion + "particle" + (i + 1) + ".ptl").equals(particle)
                    || !addParticle(particles, lookup, particle, life)) return null;
        }
        return new Blast(root, life, quake, robot.quakeHeight, List.copyOf(particles));
    }

    private static boolean addParticle(List<Particle> particles, GameScript.Lookup lookup, String path, long emitFor)
            throws IOException {
        String text = lookup.read(path);
        if (text == null) return false;
        particles.add(new Particle(path, emitFor, SkillParticleEmitter.tail(ParticleScript.parse(text))));
        return true;
    }

    private record Particle(String path, long emitFor, long tail) {}

    record Blast(String root, long duration, long quakeDuration, int quakeHeight, List<Particle> particles) {
        /** 0x5C1FF0 updates the generic blast emitters until the root ANI ends. */
        long add(List<Visual> visuals, List<SkillParticleEmitter> emitters, String label, long time,
                 double x, double depth, double height, double blastLift, double scale) {
            double groundY = depth * .29;
            double blastZ = height + blastLift;
            long end = time + duration;
            if (quakeDuration > 0) {
                // Earthquake.ani is a pooled visual attachment. Its authored box is not the robot's attack.
                double lift = quakeHeight == 0 ? 0 : blastLift;
                visuals.add(new Visual(QUAKE, label + " · 震波", 3499, time, time + quakeDuration,
                        x, groundY + .29 - height - lift).withoutAttack());
                end = Math.max(end, time + quakeDuration);
            }
            visuals.add(new Visual(root, label + " · 爆炸判定", 3500, time, time + duration,
                    x, groundY - blastZ, null, scale, true));
            for (int i = 0; i < particles.size(); i++) {
                Particle p = particles.get(i);
                String effect = i == 0 ? " · 机身碎片" : " · 爆炸粒子 " + i;
                emitters.add(new SkillParticleEmitter(p.path, label + effect, time, p.emitFor, 0,
                        x, groundY, blastZ, Double.NaN, i == 0 ? 1 : scale, true));
                end = Math.max(end, time + p.emitFor + p.tail);
            }
            return end;
        }
    }
}
