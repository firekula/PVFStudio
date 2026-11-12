package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** ACT1 CNGunnerBullet's loaded elemental shots and element 1's conditional explosion #22213. */
final class Act1FlameBullet {
    static final String PATH = "skill/gunner/flamebullet.skl";
    static final String HIT = "flamebullet:hit";
    static final String WALL = "flamebullet:wall";
    private static final String CHARACTER = "character/gunner/gunner.chr";
    private static final String EFFECT = "character/gunner/effect/animation/";
    private static final String FREEZE = "skill/gunner/freezebullet.skl", SILVER = "skill/gunner/silverbullet.skl";
    private static final String SILVER_TRAIL = "character/gunner/effect/particle/silverbulletparticle.ptl";

    private enum Element {
        FIRE("flame","爆炎弹",HIT), ICE("freeze","冰冻弹","freezebullet:hit"), LIGHT("silver","银弹","silverbullet:hit");
        final String prefix, label, choice;
        Element(String prefix,String label,String choice){this.prefix=prefix;this.label=label;this.choice=choice;}
        String art(){return EFFECT+"bullet/"+prefix+"pistol.ani";}
    }

    private static Element element(String path){
        return switch(path){case PATH->Element.FIRE;case FREEZE->Element.ICE;case SILVER->Element.LIGHT;default->null;};
    }
    private static final String EXPLOSION = "passiveobject/character/gunner/flamebulletfireexplosion.obj";
    private static final long CONTACT_AGE = 140;
    private static final int HIDDEN_AGE = 70;

    private Act1FlameBullet() {}

    static ParticleScript particleScript(String path,ParticleScript script){
        if(!path.equals(SILVER_TRAIL)||script.passive())return script;
        var angles=new ParticleScript.Vec(script.angles().x(),-30,script.angles().z());
        return new ParticleScript(script.position(),script.axes(),script.term(),script.changing(),script.gravity(),
                angles,script.life(),script.landing(),script.alphaStart(),script.alphaEnd(),script.frequency(),script.maximum(),
                script.createTime(),script.area(),script.areaAngles(),script.rangeType(),script.horizontalRange(),
                script.verticalRange(),script.random(),script.inOrder(),script.passive(),script.layer(),script.objects(),script.warnings());
    }

    static boolean selected(String path, String choice) {
        var element=element(path);
        return element!=null&&(choice.equals(element.choice)||element==Element.FIRE&&choice.equals(WALL));
    }

    static List<GameEffectRecipes.Effect> choices(String path,GameScript.Lookup lookup) throws IOException {
        var element=element(path);
        if(element==null||lookup.read(element.art())==null)return List.of();
        var hit=new GameEffectRecipes.Effect(element.choice,element.label+" · 装填后命中","",path,
                "装填后用左轮向哥布林发射一枚"+element.label);
        if(element!=Element.FIRE)return List.of(hit);
        if(!EXPLOSION.equals(GameEffectRecipes.passiveObject(lookup,22213)))return List.of();
        return List.of(hit,new GameEffectRecipes.Effect(WALL,"爆炎弹 · 装填后撞墙","",path,"装填后用左轮触发墙面火花和爆炸"));
    }

    static Act1SkillSequence appendShot(String skill,Act1SkillSequence loaded,String text,GameScript.Lookup lookup,
                                        Map<String,String> actions,int level,String weaponPath,boolean wall) throws IOException {
        var element=element(skill);
        if(loaded==null||element==null)return null;
        if(element==Element.FIRE&&!EXPLOSION.equals(GameEffectRecipes.passiveObject(lookup,22213)))return null;
        var fields=GameScript.fields(text);
        if(element==Element.FIRE&&Act1SkillSequence.staticValue(fields,0,2)<1
                ||Act1SkillSequence.levelValue(fields,level,0,7,0,1000)<1)return null;
        String label=element.label;
        String weaponText = weaponPath == null || weaponPath.isEmpty() ? null : lookup.read(weaponPath);
        var weapon = GameScript.fields(weaponText);
        int subtype = GameScript.integer(GameScript.value(weapon, "sub type"), 0);
        if (subtype != 0) return null;
        var gun = Act1GunnerPrecisionCast.gun(lookup, subtype, true, weapon);
        if (gun == null) return null;
        int attackSpeed = Act1GunnerPrecisionCast.number(GameScript.value(GameScript.fields(lookup.read(CHARACTER)), "attack speed"))
                + GameScript.integer(GameScript.value(weapon, "attack speed"), 0);
        if (attackSpeed < 100 || attackSpeed > 5000) return null;

        String prepare = "attack motion:0", release = "etc motion:1", wait = "waiting motion";
        var ready = Act1SkillSequence.frames(CHARACTER, actions.get(prepare), lookup);
        var firing = Act1SkillSequence.frames(CHARACTER, actions.get(release), lookup);
        int hold = ready.size() - 1;
        if (hold < 1 || ready.get(hold).delay() < 10000 || firing.size() < 2) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 500);
        long attack = cast + 160;
        long draw = Math.max(1, gun.draw() * attackSpeed / 1000);
        long drawn = attack + (Act1SkillSequence.duration(ready, hold, null) * 1000 + draw - 1) / draw;
        int reaction = Math.max(1, gun.reaction() * 1000 / attackSpeed);
        long fire = drawn + reaction;
        long contact = fire + CONTACT_AGE;
        int reload = Math.max(1, gun.reload() * attackSpeed / 1000);
        long recovered = fire + gun.after() * 1000L / attackSpeed + Math.max(125, 250000 / reload);

        String ptl = lookup.read(gun.particle());
        String art=element.art(),artText=lookup.read(art);
        if(ptl==null||artText==null)return null;
        var particle = ParticleScript.parse(ptl);
        var axis = particle.axes().get(0);
        if (!particle.passive() || particle.objects().size() != 1 || particle.objects().get(0).index() != 22002
                || !axis.type().equals("straight line") || axis.start() != axis.end() || particle.gravity() != 0
                || particle.axes().subList(1, 3).stream().anyMatch(other -> !other.type().equals("stop"))) return null;
        var path = new ParticleTimeline(particle.previewEmission(1, 0), gun.particle().hashCode(), 0);
        // State 8 places a first downward shot at (44,0,90), independent of the muzzle flash's PTL position.
        var start = path.sample(0, 44, 0, 90, false).particles();
        var impact = path.sample(CONTACT_AGE, 44, 0, 90, false).particles();
        if (start.isEmpty() || impact.isEmpty() || impact.get(0).z() <= 0) return null;
        var origin = start.get(0);
        var hit = impact.get(0);
        double dx = hit.x() - origin.x();
        double dz = hit.z() - origin.z() - (hit.y() - origin.y());
        var motion = new SkillMotion(fire, CONTACT_AGE, dx * 1000 / CONTACT_AGE, 0, dz * 1000 / CONTACT_AGE, 0, origin.z());
        // 0x5A7B30 adds the elemental graphic over the original projectile, without replacing its active ANI.
        var animation = ResourceScript.animation(artText);
        double pitch = Math.atan2(-dz, dx);
        var rotated = new ResourceScript.Animation(animation.loop(),
                animation.frames().stream().map(frame -> frame.withRotation(frame.rotation() + pitch)).toList(), animation.warnings());
        var artStyle = new VisualStyle(null, 0, List.of(new VisualKeyframe(0, 1, 0),
                new VisualKeyframe(HIDDEN_AGE, 1, 0), new VisualKeyframe(HIDDEN_AGE + 1, 1, 1)), Map.of(), false, 0, rotated);

        var poses = new ArrayList<Pose>();
        loaded.poses().stream().filter(pose -> !pose.action().equals(wait)).forEach(poses::add);
        poses.add(new Pose(wait, cast, attack));
        poses.add(new Pose(prepare, attack, drawn, null, true, null, 0, hold));
        poses.add(new Pose(release, drawn, recovered, reaction, false, null));
        var visuals = new ArrayList<>(loaded.visuals());
        var emitters = new ArrayList<>(loaded.emitters());
        visuals.add(new Visual(art,label+" · 属性弹道",3502,fire,contact,
                origin.x(),origin.y(),motion,1,true,artStyle));
        addProjectileCollision(lookup,visuals,particle.objects().get(0).index(),label,fire,contact,origin,motion);
        long trailEnd=contact;
        if(element==Element.LIGHT&&lookup.read(SILVER_TRAIL)!=null){
            // Downward pistol branch in 0x5A7B30 applies -30 pitch to the silver particle emitter.
            emitters.add(new SkillParticleEmitter(SILVER_TRAIL,label+" · 光粒拖尾",fire,CONTACT_AGE,0,
                    origin.x(),origin.y(),0,Double.NaN,1,true,1,false,motion));
            trailEnd=contact+SkillParticleEmitter.tail(ParticleScript.parse(lookup.read(SILVER_TRAIL)));
        }
        var muzzle = particle.position();
        long muzzleEnd = addClip(visuals, lookup, Act1GunnerPrecisionCast.muzzle(subtype, true), label+" · 左轮枪口",
                fire, muzzle.x(), muzzle.y() * .29 - muzzle.z(), List.of(0, 0));
        long impactEnd = addClip(visuals, lookup, EFFECT + "hiteffect/" + (wall ? "collisionwall" : "downdefault") + ".ani",
                wall?label+" · 子弹撞墙":label+" · 子弹命中", contact, hit.x(), hit.y() - hit.z(), List.of());
        if (muzzleEnd == fire || impactEnd == contact) return null;
        long effectEnd=element==Element.FIRE?explosion(lookup,visuals,emitters,contact,hit):contact+250;
        if(effectEnd==contact)return null;
        if (!wall) {
            Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay",label+" · 哥布林",attack,contact,hit.x(),null);
            Act1SwordAsuraSupplement.addTarget(visuals,lookup,"damage1",label+" · 哥布林受击",contact,contact+250,hit.x(),null);
            long restEnd=Math.max(effectEnd,trailEnd);
            if(restEnd>contact+250)
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay",label+" · 哥布林",contact+250,restEnd,hit.x(),null);
        }
        long end=Math.max(Math.max(effectEnd,trailEnd),Math.max(impactEnd,muzzleEnd));
        for (var visual : loaded.visuals()) end = Math.max(end, visual.end());
        for (var emitter : loaded.emitters()) end = Math.max(end, emitter.start() + emitter.emitFor()
                + SkillParticleEmitter.tail(ParticleScript.parse(lookup.read(emitter.path()))));
        if (end > recovered) poses.add(new Pose(wait, recovered, end));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters),
                wall ? "装填爆炎弹后向下开一枪，触墙时播放原版火花与爆炸。墙面位置采用固定取样。"
                        : "装填"+label+"后向下开一枪，保留原始子弹判定；以固定哥布林位置演示命中"
                        +(element==Element.FIRE?"及其独立爆炸。":"。属性伤害与冻结概率由命中状态结算。"));
    }

    private static void addProjectileCollision(GameScript.Lookup lookup,List<Visual> visuals,int object,String label,
                                               long fire,long contact,ParticleTimeline.Particle origin,SkillMotion motion) throws IOException {
        var motions=Act1SkillSequence.objectMotions(lookup,object);
        if(motions.isEmpty())return;
        String path=motions.get(0);
        var source=ResourceScript.animation(lookup.read(path));
        if(source.frames().stream().noneMatch(f->!f.combat().attack().isEmpty()))return;
        var attack=new ResourceScript.Animation(source.loop(),
                source.frames().stream().map(f->f.withImage("")).toList(),source.warnings());
        var style=new VisualStyle(null,0,List.of(),Map.of(),false,0,attack);
        visuals.add(new Visual(path,label+" · 弹体判定",3502,fire,contact,origin.x(),origin.y(),motion,1,true,style));
    }

    private static long explosion(GameScript.Lookup lookup, List<Visual> visuals,
                                  List<SkillParticleEmitter> emitters, long contact,
                                  ParticleTimeline.Particle hit) throws IOException {
        var fields = GameScript.fields(lookup.read(EXPLOSION));
        String root = GameScript.resolve(EXPLOSION, GameScript.value(fields, "basic motion"), lookup);
        var references = GameScript.values(fields, "string data");
        if (root == null || references.size() != 4) return contact;
        long life = new AnimationTimeline(ResourceScript.animation(lookup.read(root))).duration();
        if (life < 1 || life > 3000) return contact;
        long end = contact + life;
        visuals.add(new Visual(root, "爆炎弹 · 爆炸判定", 3553, contact, end, hit.x(), hit.y() - hit.z()));
        // Both native contact handlers create 22213. 0x5C1FF0 runs its four authored PTLs until its root ANI ends.
        for (int i = 0; i < references.size(); i++) {
            String path = GameScript.resolve(EXPLOSION, references.get(i), lookup);
            if (path == null || !path.endsWith(".ptl")) return contact;
            var particle = ParticleScript.parse(lookup.read(path));
            emitters.add(new SkillParticleEmitter(path, "爆炎弹 · 爆炸 " + (i + 1), contact, life, 0,
                    hit.x(), hit.y(), hit.z(), Double.NaN, 1, false));
            end = Math.max(end, contact + life + SkillParticleEmitter.tail(particle));
        }
        return end;
    }

    private static long addClip(List<Visual> visuals, GameScript.Lookup lookup, String path, String label,
                                long start, double x, double y, List<Integer> variations) throws IOException {
        String source = lookup.read(path);
        if (source == null) return start;
        long end = start + new AnimationTimeline(ResourceScript.animation(source)).duration();
        if (end > start) visuals.add(new Visual(path, label, 3552, start, end, x, y, null, 1, true, VisualStyle.DEFAULT, variations));
        return end;
    }
}
